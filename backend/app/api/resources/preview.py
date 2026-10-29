from uuid import UUID
from fastapi import Body, Depends, Header, Query
from app.schemas import CorrectionDTO, NoteDTO, RecordMutation, TypeDTO, PreviewRequest
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.mapping import command

def register(router, context, service):
    @router.post('/preview')
    def interpretation_preview(body: PreviewRequest, user_id=Depends(context.owner)):
        return service.interpretation_preview(body=command(body), user_id=user_id)

    @router.post('/preview/ai')
    def ai_preview(body: PreviewRequest, user_id=Depends(context.owner), db=Depends(context.session)):
        return service.ai_preview(body=command(body), user_id=user_id, db=db)
