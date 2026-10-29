from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.post('/auth/enroll')
    def enroll_device(body: Enrollment, db=Depends(context.session)):
        return service.enroll_device(body=command(body), db=db)

    @router.post('/auth/revoke')
    def revoke_device(user_id=Depends(context.owner), authorization: str=Header(), db=Depends(context.session)):
        return service.revoke_device(user_id=user_id, authorization=authorization, db=db)

    @router.get('/config')
    def config(user_id=Depends(context.owner)):
        return service.config(user_id=user_id)

    @router.get('/diagnostics')
    def diagnostics(user_id=Depends(context.owner), db=Depends(context.session)):
        return service.diagnostics(user_id=user_id, db=db)
