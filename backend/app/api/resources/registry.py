from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.get('/types')
    def types(user_id=Depends(context.owner), db=Depends(context.session)):
        return service.types(user_id=user_id, db=db)

    @router.post('/types')
    def create_type(body: TypeDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.create_type(body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.patch('/types/{type_id}')
    def edit_type(type_id: UUID, body: TypeDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.edit_type(type_id=type_id, body=command(body), operation_key=operation_key, user_id=user_id, db=db)

    @router.post('/rules')
    def put_rule(body: RuleDTO, operation_key=Depends(context.operation_key), user_id=Depends(context.owner), db=Depends(context.session)):
        return service.put_rule(body=command(body), operation_key=operation_key, user_id=user_id, db=db)
