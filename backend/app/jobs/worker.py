import random
import re
import time
import logging
import json
import socket
from sqlalchemy.exc import SQLAlchemyError
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid5
from sqlalchemy import and_, or_, select, func
from app.ai.provider import OpenAICompatibleProvider, ProviderUnavailable, RetryableProviderError, ProviderContextError
from app.config import settings
from app.persistence.database import SessionFactory
from app.persistence.models import Job, ServiceHeartbeat, now, uid
from app.services import lock_user


def claim(factory):
    with factory() as db:
        current = now()
        job = db.scalar(select(Job).where(or_(and_(Job.status == "queued", Job.next_attempt_at <= current), and_(Job.status == "running", Job.lease_until < current))).order_by(Job.next_attempt_at, Job.id).with_for_update(skip_locked=True).limit(1))
        if not job:
            return None
        job.status = "running"; job.attempts += 1; job.lease_token = uid()
        job.lease_until = (datetime.now(timezone.utc) + timedelta(minutes=2)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        db.commit()
        return job.id, job.lease_token


def process_one(factory=SessionFactory, provider=None, max_attempts: int | None = None):
    from app.application.process_job import ProcessJob
    from app.infrastructure.gateways import ProviderAdapter
    from app.infrastructure.persistence.unit_of_work import factory_for
    claimed = claim(factory)
    if not claimed:
        return False
    managed_provider = provider is None
    adapter = ProviderAdapter(provider or OpenAICompatibleProvider(settings))
    return ProcessJob(factory_for(factory), adapter, settings, lock_user).execute(claimed, managed_provider, max_attempts)


def run():
    # At most one in-flight claim per thread; multiple processes use SKIP LOCKED.
    def loop():
        while True:
            try:
                if not process_one():
                    time.sleep(1)
            except SQLAlchemyError:
                # No SQL parameters or note text in operational logs. Expired leases recover.
                logging.getLogger(__name__).warning("Database unavailable; retrying durable jobs")
                time.sleep(5)
    with ThreadPoolExecutor(max_workers=max(1, min(settings.worker_concurrency, 8))) as pool:
        futures = [pool.submit(loop) for _ in range(max(1, min(settings.worker_concurrency, 8)))]
        while True:
            for future in futures:
                if future.done(): future.result()
            try:
                with SessionFactory() as db:
                    db.merge(ServiceHeartbeat(name="worker:" + socket.gethostname(), seen_at=now())); db.commit()
            except SQLAlchemyError:
                logging.getLogger(__name__).warning("Worker heartbeat database unavailable")
            time.sleep(10)


if __name__ == "__main__":
    run()
