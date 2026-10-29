"""Compatibility adapter for historical DB context callers. New workflows use application.ai_context."""
from app.application.ai_context import apply_rules
from app.application import ai_context
from app.infrastructure.persistence.unit_of_work import SqlUnitOfWork

def context_for(session, note):
    return ai_context.context_for(session if isinstance(session, SqlUnitOfWork) else SqlUnitOfWork(session), note)

def relevant_corrections(session, note):
    return ai_context.relevant_corrections(session if isinstance(session, SqlUnitOfWork) else SqlUnitOfWork(session), note)
