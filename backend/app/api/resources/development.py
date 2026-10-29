from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.post('/ideas/{record_id}/develop')
    def develop(record_id: UUID, body: dict=Body(default={}), operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.develop(record_id=record_id, body=body, operation_key=operation_key, user_id=user_id, db=db)

    @router.get('/jobs/{job_id}')
    def get_job(job_id: UUID, user_id=Depends(context.owner), db=Depends(context.session)):
        return service.get_job(job_id=job_id, user_id=user_id, db=db)
