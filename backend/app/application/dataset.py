import base64
import json
from app.domain.entities import User
from app.domain.time import now
from app.domain.errors import ApplicationError
from app.application.values import Consent
from app.application.mutations import mutate
POLICY = "learning-v1"

def wire(row):
    return {"id": row.id, "schemaVersion": row.schema_version, "groupKey": row.group_key,
            "origin": row.origin, "eventKind": row.event_kind, "labelSource": row.label_source,
            "createdAt": row.created_at, "data": row.data}


class DatasetService:
    def summary(self, owner, db):
        user = db.get(User, owner)
        counts = db.observation_counts(owner)
        return {'schemaVersion': 1, 'policyVersion': POLICY, 'contributionEnabled': user.dataset_consent, 'consentUpdatedAt': user.dataset_consent_at, 'examples': dict(counts), 'total': sum((n for _, n in counts))}

    def consent(self, body: Consent, idempotency_key: str, owner, db):

        def action():
            user = db.get(User, owner)
            user.dataset_consent = body.enabled
            user.dataset_consent_at = now()
            return {'contributionEnabled': body.enabled, 'policyVersion': POLICY, 'consentUpdatedAt': user.dataset_consent_at}
        return mutate(db, owner, idempotency_key, 'dataset/consent', body.to_dict(), action)

    def examples(self, cursor: str, limit: int, owner, db):
        position = None
        if cursor:
            try:
                position = json.loads(base64.urlsafe_b64decode(cursor))
                timestamp, identity = position
                if not isinstance(timestamp, str) or not isinstance(identity, str):
                    raise ValueError()
            except (ValueError, TypeError):
                raise ApplicationError(422, 'Invalid dataset cursor')
        rows = db.observation_page(owner, position, limit + 1)
        page, size = ([], 0)
        for row in rows[:limit]:
            length = len(json.dumps(wire(row), ensure_ascii=False).encode())
            if page and size + length > 1048576:
                break
            page.append(row)
            size += length
        next_cursor = base64.urlsafe_b64encode(json.dumps([page[-1].created_at, page[-1].id]).encode()).decode() if len(rows) > len(page) else None
        return {'examples': [wire(row) for row in page], 'nextCursor': next_cursor}
