from typing import Literal
from fastapi import APIRouter, Depends, Header, Query
from app.schemas import DTO
from app.api.mapping import command

class Consent(DTO):
    enabled: bool
    policyVersion: Literal["learning-v1"]

def router_for(factory, service, identity):
    router = APIRouter(prefix="/api/v1/dataset")
    def session():
        with factory() as db: yield db
    def user(authorization: str | None = Header(default=None), db=Depends(session)):
        return identity.authenticate(db, authorization)

    @router.get('/summary')
    def summary(owner=Depends(user), db=Depends(session)):
        return service.summary(owner=owner, db=db)

    @router.post('/consent')
    def consent(body: Consent, idempotency_key: str=Header(alias='Idempotency-Key'), owner=Depends(user), db=Depends(session)):
        return service.consent(body=command(body), idempotency_key=idempotency_key, owner=owner, db=db)

    @router.get('/examples')
    def examples(cursor: str=Query(default='', max_length=200), limit: int=Query(default=50, ge=1, le=100), owner=Depends(user), db=Depends(session)):
        return service.examples(cursor=cursor, limit=limit, owner=owner, db=db)
    return router
