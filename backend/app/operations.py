"""Content-free operator diagnostics and explicit expired-auth metadata cleanup."""
import argparse
from datetime import datetime, timedelta, timezone
import json
from sqlalchemy import delete, func, select
from app.persistence.database import SessionFactory
from app.persistence.models import AccountToken, AuthRate, Job, LoginFlow, ProviderRequest, ServiceHeartbeat, now

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["status", "prune-expired-auth"])
    args = parser.parse_args()
    with SessionFactory() as db:
        if args.command == "status":
            counts = db.execute(select(Job.status, func.count()).group_by(Job.status)).all()
            heartbeat = db.scalar(select(func.max(ServiceHeartbeat.seen_at)))
            print(json.dumps({"jobs": dict(counts), "lastWorkerHeartbeat": heartbeat, "oldestQueuedAt": db.scalar(select(func.min(Job.next_attempt_at)).where(Job.status == "queued"))}))
        else:
            old = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            usage_cutoff = (datetime.now(timezone.utc) - timedelta(days=90)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            removed = {}
            for cls, condition in [(AuthRate, AuthRate.expires_at < now()), (LoginFlow, LoginFlow.expires_at < old), (AccountToken, AccountToken.expires_at < old), (ProviderRequest, ProviderRequest.created_at < usage_cutoff), (ServiceHeartbeat, ServiceHeartbeat.seen_at < old)]:
                removed[cls.__tablename__] = db.execute(delete(cls).where(condition)).rowcount
            db.commit(); print(json.dumps({"removedExpiredMetadata": removed}))

if __name__ == "__main__": main()
