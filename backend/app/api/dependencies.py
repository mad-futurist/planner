from fastapi import Depends, Header

class RequestDependencies:
    """One dependency set per router composition; sessions are request scoped."""
    def __init__(self, factory, identity):
        def session():
            with factory() as uow:
                yield uow
        def owner(db=Depends(session), authorization: str | None = Header(default=None)):
            return identity.authenticate(db, authorization)
        def operation_key(idempotency_key: str = Header(alias="Idempotency-Key")):
            return idempotency_key
        self.session = session
        self.owner = owner
        self.operation_key = operation_key
