"""Composition root. Only wiring knows concrete SQL/provider/account adapters."""
from app.config import settings
from app.ai.provider import OpenAICompatibleProvider
from app.application.organizer import OrganizerService
from app.infrastructure.gateways import AccountSecurityAdapter, ProviderAdapter
from app.application.device_identity import DeviceIdentityService
from app.infrastructure.persistence.unit_of_work import factory_for

def organizer_dependencies(session_factory):
    identity = DeviceIdentityService(AccountSecurityAdapter())
    return factory_for(session_factory), OrganizerService(settings, lambda: ProviderAdapter(OpenAICompatibleProvider(settings)), identity), identity
