from datetime import datetime, timedelta, timezone
from sqlalchemy import func, select
from app.persistence.models import ProviderRequest, now
from app.services import lock_user
from app.config import settings

from app.application.ports import ProviderBudgetExceeded

def reserve_request(db, owner, kind):
    """Count actual provider attempts, serializing the per-owner UTC daily budget."""
    lock_user(db, owner)
    midnight = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    since = midnight.isoformat(timespec="milliseconds").replace("+00:00", "Z")
    count = db.scalar(select(func.count()).select_from(ProviderRequest).where(ProviderRequest.user_id == owner, ProviderRequest.created_at >= since))
    if count >= settings.ai_daily_request_limit: raise ProviderBudgetExceeded()
    db.add(ProviderRequest(user_id=owner, kind=kind, created_at=now()))
    db.commit()

def next_budget_window():
    return (datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
