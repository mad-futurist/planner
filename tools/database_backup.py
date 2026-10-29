"""Docker PostgreSQL backup, restore to a NEW database, and isolated restore smoke.

Archives may contain private notes. Store them encrypted with access/retention controls.
Existing databases are never overwritten or dropped by restore.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
PROJECT = []
def docker(*args):
    result = subprocess.run(["docker", "compose", *PROJECT, *args], cwd=ROOT, capture_output=True, check=False)
    if result.returncode: raise RuntimeError("Docker database operation failed: " + result.stderr.decode(errors="replace")[:500])
    return result.stdout.decode().strip()
def pg(*args): return docker("exec", "-T", "db", *args)
def user(): return pg("printenv", "POSTGRES_USER")
def safe_database(name):
    if not name.startswith("planner_restore_") and not name.startswith("planner_smoke_"): raise ValueError("Use a new database name starting planner_restore_ or planner_smoke_")
    if not name.replace("_", "").isalnum(): raise ValueError("Invalid database name")
    return name

def backup(output, database=None):
    output = output.resolve()
    if output.exists(): raise ValueError("Refusing to overwrite an existing backup")
    output.parent.mkdir(parents=True, exist_ok=True)
    remote = "/tmp/planner-backup-" + uuid4().hex + ".dump"
    try:
        pg("pg_dump", "-U", user(), "-d", database or pg("printenv", "POSTGRES_DB"), "-Fc", "-f", remote)
        docker("cp", "db:" + remote, str(output))
    finally: pg("rm", "-f", "--", remote)
    return output

def restore(archive, database):
    database = safe_database(database)
    if not archive.is_file(): raise ValueError("Backup archive is missing")
    remote = "/tmp/planner-restore-" + uuid4().hex + ".dump"
    pg("createdb", "-U", user(), "-T", "template0", database)  # Fails if target exists.
    try:
        docker("cp", str(archive.resolve()), "db:" + remote)
        pg("pg_restore", "-U", user(), "-d", database, "--exit-on-error", "--no-owner", "--no-privileges", remote)
        tables = pg("psql", "-U", user(), "-d", database, "-At", "-c", "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
        return int(tables)
    finally: pg("rm", "-f", "--", remote)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", help="Compose project, e.g. planner-prod; defaults to this checkout's project")
    commands = parser.add_subparsers(dest="command", required=True)
    a = commands.add_parser("backup"); a.add_argument("--output", type=Path, required=True)
    b = commands.add_parser("restore"); b.add_argument("--input", type=Path, required=True); b.add_argument("--database", required=True)
    commands.add_parser("smoke")
    args = parser.parse_args()
    if args.project: PROJECT.extend(["--project-name", args.project])
    if args.command == "backup": print("Backup created:", backup(args.output))
    elif args.command == "restore": print("Restored into new database:", args.database, "tables:", restore(args.input, args.database))
    else:
        source, target = "planner_smoke_" + uuid4().hex, "planner_restore_" + uuid4().hex
        pg("createdb", "-U", user(), source)
        try:
            pg("psql", "-U", user(), "-d", source, "-v", "ON_ERROR_STOP=1", "-c", "CREATE TABLE smoke(value text); INSERT INTO smoke VALUES ('preserved')")
            with tempfile.TemporaryDirectory(prefix="planner-backup-smoke-") as folder:
                archive = backup(Path(folder) / "smoke.dump", source)
                assert restore(archive, target) == 1
                assert pg("psql", "-U", user(), "-d", target, "-At", "-c", "SELECT value FROM smoke") == "preserved"
            print("Backup/restore smoke passed; only isolated synthetic databases were used")
        finally:
            pg("dropdb", "-U", user(), "--if-exists", source)
            pg("dropdb", "-U", user(), "--if-exists", target)

if __name__ == "__main__": main()
