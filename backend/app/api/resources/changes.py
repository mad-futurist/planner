from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.get('/changes')
    def changes(cursor: int=Query(default=0, ge=0), limit: int=Query(default=50, ge=1, le=100), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.changes(cursor=cursor, limit=limit, user_id=user_id, db=db)
