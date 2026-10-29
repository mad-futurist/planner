from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.patch('/records/{record_id}')
    def update_record(record_id: UUID, body: RecordMutation, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.update_record(record_id=record_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.post('/records/{record_id}/complete')
    def complete(record_id: UUID, body: VersionAction, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.complete(record_id=record_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.post('/records/{record_id}/undo')
    def undo(record_id: UUID, body: VersionAction, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.undo(record_id=record_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)
