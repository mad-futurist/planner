"""Executable source dependency rules for the two existing monoliths."""
import ast
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def violations(root=ROOT):
    errors = []
    backend = root / "backend/app"
    forbidden = {
        "domain": ("fastapi", "pydantic", "sqlalchemy", "httpx", "app.application", "app.infrastructure", "app.api", "app.persistence", "app.schemas", "app.security", "app.ai"),
        "application": ("fastapi", "pydantic", "sqlalchemy", "httpx", "app.infrastructure", "app.api", "app.persistence", "app.schemas", "app.security", "app.ai", "app.config"),
        "api": ("sqlalchemy", "app.persistence", "app.services", "app.security", "app.ai", "app.infrastructure"),
    }
    for layer, blocked in forbidden.items():
        for path in (backend / layer).rglob("*.py"):
            for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
                modules = []
                if isinstance(node, ast.Import):
                    modules = [alias.name for alias in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module:
                    modules = [node.module]
                for module in modules:
                    if any(module == name or module.startswith(name + ".") for name in blocked):
                        errors.append(f"{path.relative_to(root)}:{node.lineno}: {layer} imports {module}")
    android = root / "android/app/src/main/java/app/planner"
    for path in android.rglob("*.kt"):
        if "EntryPointAccessors.fromApplication" in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(root)}: runtime service lookup bypasses constructor injection")
    for layer, blocked in {
        "domain": ("android", "androidx", "retrofit", "com.google.gson", "app.planner.core", "app.planner.application", "app.planner.data", "app.planner.features", "app.planner.sync", "app.planner.navigation"),
        "application": ("android", "androidx", "retrofit", "com.google.gson", "app.planner.core", "app.planner.data", "app.planner.features", "app.planner.sync", "app.planner.navigation"),
        "features": ("app.planner.core.database", "app.planner.data", "app.planner.sync", "app.planner.core.Preferences"),
    }.items():
        for path in (android / layer).rglob("*.kt"):
            # Native overlay service/permission/lifecycle classes are platform adapters,
            # retained at their Android manifest/source-compatible location.
            if layer == "features" and "overlay" in path.parts:
                continue
            text = path.read_text(encoding="utf-8")
            for line, value in enumerate(text.splitlines(), 1):
                imported = re.match(r"import\s+(\S+)", value)
                if imported and any(imported[1] == name or imported[1].startswith(name + ".") for name in blocked):
                    errors.append(f"{path.relative_to(root)}:{line}: {layer} imports {imported[1]}")
                if layer == "features" and re.search(r"\bval\s+\w+\s*=\s*MutableStateFlow", value) and not re.search(r"\b(private|protected)\b", value):
                    errors.append(f"{path.relative_to(root)}:{line}: public mutable UI state")
                if layer == "features" and (".repository.dao" in value or "vm.action" in value):
                    errors.append(f"{path.relative_to(root)}:{line}: UI bypasses application commands")
                if layer == "features" and "NavHostController" in value and path.name not in {"PlannerNavHost.kt", "SettingsRoute.kt", "PlannerUI.kt"}:
                    errors.append(f"{path.relative_to(root)}:{line}: screen content receives a navigation controller")
    return errors


if __name__ == "__main__":
    found = violations()
    print("\n".join(found) if found else "Architecture boundaries: OK")
    raise SystemExit(bool(found))
