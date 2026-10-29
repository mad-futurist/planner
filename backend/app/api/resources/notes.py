from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.post('/notes')
    def ingest(body: NoteDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.ingest(body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.get('/notes')
    def list_notes(cursor: str=Query(default='', max_length=200), limit: int=Query(default=50, ge=1, le=100), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.list_notes(cursor=cursor, limit=limit, user_id=user_id, db=db)

    @router.get('/notes/{note_id}')
    def get_note(note_id: UUID, user_id=Depends(context.owner), db=Depends(context.session)):
        return service.get_note(note_id=note_id, user_id=user_id, db=db)

    @router.patch('/notes/{note_id}')
    def edit_note(note_id: UUID, body: NoteEdit, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.edit_note(note_id=note_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.post('/notes/{note_id}/reprocess')
    def reprocess(note_id: UUID, body: dict=Body(default={}), operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.reprocess(note_id=note_id, body=body, operation_key=operation_key, user_id=user_id, db=db)

    @router.get('/notes/{note_id}/interpretation')
    def interpretation(note_id: UUID, user_id=Depends(context.owner), db=Depends(context.session)):
        return service.interpretation(note_id=note_id, user_id=user_id, db=db)

    @router.post('/notes/{note_id}/corrections')
    def correction(note_id: UUID, body: CorrectionDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.correction(note_id=note_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.post('/interpretations/{history_id}/corrections')
    def interpretation_correction(history_id: UUID, body: CorrectionDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.interpretation_correction(history_id=history_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)
