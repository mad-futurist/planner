"""SQLAlchemy identity mapping and transaction adapter. No ORM object crosses this boundary."""
from copy import deepcopy
from dataclasses import fields
from app.domain import entities
from app.persistence import models

class SqlUnitOfWork:
    def __init__(self, session):
        self.session = session
        self._tracked = {}
        self._by_row = {}
        self._original = {}

    def _sync(self):
        for value, row in self._tracked.values():
            original = self._original[id(value)]
            for f in fields(value):
                current = getattr(value, f.name)
                stored = getattr(row, f.name)
                if current != original[f.name]:
                    setattr(row, f.name, deepcopy(current))
                    original[f.name] = deepcopy(current)
                elif stored != original[f.name]:
                    # SQLAlchemy may autoflush a generated sequence while executing a read.
                    setattr(value, f.name, deepcopy(stored))
                    original[f.name] = deepcopy(stored)

    def _snapshot(self, row, refresh=False):
        if row is None:
            return None
        cls = getattr(entities, type(row).__name__)
        key = id(row)
        if key in self._by_row and not refresh:
            return self._by_row[key]
        value = cls(**{f.name: deepcopy(getattr(row, f.name)) for f in fields(cls)})
        self._tracked[id(value)] = (value, row)
        self._original[id(value)] = {f.name: deepcopy(getattr(value, f.name)) for f in fields(value)}
        self._by_row[key] = value
        return value

    def get(self, cls, identity):
        self._sync()
        return self._snapshot(self.session.get(getattr(models, cls.__name__), identity))

    def add(self, value):
        if id(value) not in self._tracked:
            cls = getattr(models, type(value).__name__)
            row = cls(**{f.name: deepcopy(getattr(value, f.name)) for f in fields(value)})
            self.session.add(row)
            self._tracked[id(value)] = (value, row)
            self._original[id(value)] = {f.name: deepcopy(getattr(value, f.name)) for f in fields(value)}
            self._by_row[id(row)] = value

    def merge(self, value):
        self._sync()
        cls = getattr(models, type(value).__name__)
        row = self.session.merge(cls(**{f.name: deepcopy(getattr(value, f.name)) for f in fields(value)}))
        self._tracked[id(value)] = (value, row)
        self._original[id(value)] = {f.name: deepcopy(getattr(value, f.name)) for f in fields(value)}
        self._by_row[id(row)] = value

    def flush(self):
        self._sync()
        self.session.flush()
        for value, row in self._tracked.values():
            for f in fields(value):
                setattr(value, f.name, deepcopy(getattr(row, f.name)))
                self._original[id(value)][f.name] = deepcopy(getattr(row, f.name))

    def commit(self):
        self.flush()
        self.session.commit()

    def rollback(self):
        self.session.rollback()
        self._tracked.clear()
        self._by_row.clear()
        self._original.clear()

    def close(self):
        self.session.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.session.__exit__(*args)


    def lock_owner(self, user_id):
        import hashlib
        from sqlalchemy import text
        self._sync()
        if self.session.bind.dialect.name == "postgresql":
            key = int(hashlib.sha256(user_id.encode()).hexdigest()[:15], 16)
            self.session.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": key})

    def owned(self, kind, entity_id, owner, lock=False):
        from sqlalchemy import select
        self._sync()
        cls = getattr(models, kind.__name__)
        query = select(cls).where(cls.id == entity_id, cls.user_id == owner)
        if lock: query = query.with_for_update()
        return self._snapshot(self.session.scalar(query))

    def recent_note_count(self, owner, since):
        from sqlalchemy import select, func
        self._sync()
        return self.session.scalar(select(func.count()).select_from(models.Note).where(models.Note.user_id == owner, models.Note.received_at >= since))

    def note_page(self, owner, position, limit):
        from sqlalchemy import select, and_, or_
        self._sync(); cls = models.Note
        query = select(cls).where(cls.user_id == owner)
        if position:
            timestamp, entity_id = position
            query = query.where(or_(cls.created_at < timestamp, and_(cls.created_at == timestamp, cls.id < entity_id)))
        return [self._snapshot(row) for row in self.session.scalars(query.order_by(cls.created_at.desc(), cls.id.desc()).limit(limit)).all()]

    def records_for_note(self, note_id, owner=None, unconfirmed=False):
        from sqlalchemy import select
        self._sync(); cls = models.Record
        query = select(cls).where(cls.source_note_id == note_id)
        if owner is not None: query = query.where(cls.user_id == owner)
        if unconfirmed: query = query.where(cls.confirmed.is_(False))
        return [self._snapshot(row) for row in self.session.scalars(query).all()]

    def latest_history(self, note_id):
        from sqlalchemy import select
        self._sync(); cls = models.History
        return self._snapshot(self.session.scalar(select(cls).where(cls.note_id == note_id).order_by(cls.created_at.desc()).limit(1)))

    def owner_types(self, owner):
        from sqlalchemy import select
        self._sync(); cls = models.TypeDefinition
        return [self._snapshot(row) for row in self.session.scalars(select(cls).where(cls.user_id == owner)).all()]

    def type_versions(self, type_id):
        from sqlalchemy import select
        self._sync(); cls = models.TypeDefinition
        return [self._snapshot(row) for row in self.session.scalars(select(cls).where(cls.id == type_id)).all()]

    def change_page(self, owner, cursor, limit):
        from sqlalchemy import select
        self._sync(); cls = models.Change
        return [self._snapshot(row) for row in self.session.scalars(select(cls).where(cls.user_id == owner, cls.sequence > cursor).order_by(cls.sequence).limit(limit)).all()]

    def diagnostics(self, owner, since):
        from sqlalchemy import select, func
        self._sync(); db = self.session
        counts = db.execute(select(models.Job.status, func.count()).where(models.Job.user_id == owner).group_by(models.Job.status)).all()
        oldest = db.scalar(select(func.min(models.Job.next_attempt_at)).where(models.Job.user_id == owner, models.Job.status == "queued"))
        used = db.scalar(select(func.count()).select_from(models.ProviderRequest).where(models.ProviderRequest.user_id == owner, models.ProviderRequest.created_at >= since))
        return dict(counts), oldest, used

    def find_job(self, note_id, version, kind, record_id, record_version):
        from sqlalchemy import select
        self._sync(); cls = models.Job
        return self._snapshot(self.session.scalar(select(cls).where(cls.note_id == note_id, cls.input_version == version, cls.kind == kind, cls.record_id == record_id, cls.record_version == record_version)))

    def reserve_request(self, owner, kind):
        from app.provider_budget import reserve_request
        self._sync(); reserve_request(self.session, owner, kind)

    def input_observation(self, note, records):
        from app.learning import captured
        self.flush(); captured(self.session, note, records)

    def correction_observation(self, note, correction, kind):
        from app.learning import corrected
        self.flush(); corrected(self.session, note, correction, kind)

    def prediction_observation(self, note, owner, **kwargs):
        from app.learning import predicted
        self.flush()
        result = kwargs.get("result")
        if result is not None and hasattr(result, "to_dict"):
            from app.schemas import Development, Extraction
            kwargs["result"] = (Development if hasattr(result, "steps") else Extraction).model_validate(result.to_dict(mode="json"))
        provider = kwargs.get("provider")
        if hasattr(provider, "adapter"): kwargs["provider"] = provider.adapter
        predicted(self.session, note, owner, **kwargs)

    def context_types(self, owner):
        from sqlalchemy import select, func, and_
        self._sync(); cls = models.TypeDefinition
        latest = select(cls.id, func.max(cls.schema_version).label("version")).where(cls.user_id == owner).group_by(cls.id).subquery()
        return [self._snapshot(row) for row in self.session.scalars(select(cls).join(latest, and_(cls.id == latest.c.id, cls.schema_version == latest.c.version)).where(cls.data["enabled"].as_boolean().is_(True)).order_by(cls.id).limit(50)).all()]

    def context_rules(self, owner):
        from sqlalchemy import select
        self._sync(); cls = models.Rule
        return [self._snapshot(row) for row in self.session.scalars(select(cls).where(cls.user_id == owner, cls.data["enabled"].as_boolean().is_(True), cls.data["explicitlyApproved"].as_boolean().is_(True)).order_by(cls.id).limit(20)).all()]

    def context_corrections(self, owner):
        from sqlalchemy import select
        self._sync(); cls = models.Correction
        return [self._snapshot(row) for row in self.session.scalars(select(cls).where(cls.user_id == owner).order_by(cls.created_at.desc()).limit(50)).all()]

    def job_owner(self, job_id):
        from sqlalchemy import select
        self._sync()
        return self.session.scalar(select(models.Job.user_id).where(models.Job.id == job_id))

    def locked_job(self, job_id):
        from sqlalchemy import select
        self._sync(); cls = models.Job
        row = self.session.scalar(select(cls).where(cls.id == job_id).with_for_update().execution_options(populate_existing=True))
        if row is not None and id(row) in self._by_row:
            previous = self._by_row.pop(id(row)); self._tracked.pop(id(previous), None)
        return self._snapshot(row)

    def locked_note(self, note_id):
        from sqlalchemy import select
        self._sync(); cls = models.Note
        return self._snapshot(self.session.scalar(select(cls).where(cls.id == note_id).with_for_update()))

    def observation_counts(self, owner):
        from sqlalchemy import select, func
        self._sync(); cls = models.LearningSample
        return self.session.execute(select(cls.event_kind, func.count()).where(cls.user_id == owner).group_by(cls.event_kind)).all()

    def observation_page(self, owner, position, limit):
        from sqlalchemy import select, and_, or_
        self._sync(); cls = models.LearningSample
        query = select(cls).where(cls.user_id == owner)
        if position:
            timestamp, identity = position
            query = query.where(or_(cls.created_at > timestamp, and_(cls.created_at == timestamp, cls.id > identity)))
        return [self._snapshot(row) for row in self.session.scalars(query.order_by(cls.created_at, cls.id).limit(limit)).all()]

    def lock_bucket(self, key):
        from sqlalchemy import text
        self._sync()
        if self.session.bind.dialect.name == "postgresql":
            self.session.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": int(key[:15], 16)})

    def user_by_email(self, email):
        from sqlalchemy import select
        self._sync()
        return self._snapshot(self.session.scalar(select(models.User).where(models.User.email == email)))

    def _refresh(self, row):
        if row is not None and id(row) in self._by_row:
            previous = self._by_row.pop(id(row))
            self._tracked.pop(id(previous), None); self._original.pop(id(previous), None)
        return self._snapshot(row)

    def locked_user(self, owner):
        from sqlalchemy import select
        self._sync(); cls = models.User
        return self._refresh(self.session.scalar(select(cls).where(cls.id == owner).with_for_update().execution_options(populate_existing=True)))

    def recent_email_token(self, owner, purpose, threshold):
        from sqlalchemy import select
        self._sync(); cls = models.AccountToken
        return self._snapshot(self.session.scalar(select(cls).where(cls.user_id == owner, cls.purpose == purpose, cls.expires_at > threshold, cls.used.is_(False)).limit(1)))

    def token_owner(self, digest):
        from sqlalchemy import select
        self._sync()
        return self.session.scalar(select(models.AccountToken.user_id).where(models.AccountToken.digest == digest))

    def locked_token(self, digest):
        from sqlalchemy import select
        self._sync(); cls = models.AccountToken
        return self._refresh(self.session.scalar(select(cls).where(cls.digest == digest).with_for_update()))

    def invalidate_sessions(self, owner):
        from sqlalchemy import update, delete
        self._sync()
        self.session.execute(update(models.Device).where(models.Device.user_id == owner).values(revoked=True))
        self.session.execute(update(models.AccountToken).where(models.AccountToken.user_id == owner).values(used=True))
        self.session.execute(delete(models.LoginFlow).where(models.LoginFlow.link_user_id == owner))

    def expire_all(self):
        self.flush(); self.session.expire_all()
        self._tracked.clear(); self._by_row.clear(); self._original.clear()

    def locked_login_flow(self, digest):
        from sqlalchemy import select
        self._sync(); cls = models.LoginFlow
        return self._refresh(self.session.scalar(select(cls).where(cls.state_digest == digest).with_for_update().execution_options(populate_existing=True)))

    def login_owner(self, digest):
        from sqlalchemy import select
        self._sync()
        return self.session.scalar(select(models.LoginFlow.user_id).where(models.LoginFlow.state_digest == digest))

    def user_exists(self, owner):
        from sqlalchemy import select
        self._sync()
        return self.session.scalar(select(models.User.id).where(models.User.id == owner)) is not None

    def delete_account_data(self, owner, synthetic):
        from sqlalchemy import update, delete, or_
        self.flush(); db = self.session
        if synthetic:
            db.execute(update(models.LearningSample).where(models.LearningSample.user_id == owner).values(user_id=None, origin="synthetic"))
        else:
            db.execute(delete(models.LearningSample).where(models.LearningSample.user_id == owner))
        for cls in (models.Operation, models.Change, models.Correction, models.History, models.Job, models.Record, models.ProviderRequest, models.TypeDefinition, models.Rule, models.AccountToken, models.ExternalIdentity, models.Invitation, models.Device):
            db.execute(delete(cls).where(cls.user_id == owner))
        db.execute(delete(models.LoginFlow).where(or_(models.LoginFlow.user_id == owner, models.LoginFlow.link_user_id == owner)))
        db.execute(delete(models.Note).where(models.Note.user_id == owner)); db.execute(delete(models.User).where(models.User.id == owner))
        self._tracked.clear(); self._by_row.clear(); self._original.clear()

    def locked_invitation(self, digest):
        from sqlalchemy import select
        self._sync(); cls = models.Invitation
        return self._refresh(self.session.scalar(select(cls).where(cls.digest == digest).with_for_update()))

def factory_for(session_factory):
    return lambda: SqlUnitOfWork(session_factory())
