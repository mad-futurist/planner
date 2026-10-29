"""Organizer resource-router composition. Business operations live in application."""
from fastapi import APIRouter
from app.api.dependencies import RequestDependencies
from app.api.organizer_contracts import Enrollment, VersionAction, NoteEdit, RuleDTO
from app.api.resources import connection, preview, notes, records, registry, development, changes

def router_for(factory, service, identity):
    router = APIRouter(prefix="/api/v1")
    context = RequestDependencies(factory, identity)
    for resource in (connection, preview, notes, records, registry, development, changes):
        resource.register(router, context, service)
    return router
