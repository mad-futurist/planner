from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from sqlalchemy import text
from sqlalchemy import select
from datetime import datetime, timedelta, timezone
from app.persistence.models import ServiceHeartbeat
from sqlalchemy.exc import IntegrityError
from app.api.routes import router_for
from app.api.accounts import router_for as account_router_for
from app.api.dataset import router_for as dataset_router_for
from app.config import settings
from app.domain.errors import ApplicationError
from app.persistence.database import SessionFactory


class BodyLimitMiddleware:
    def __init__(self, app): self.app = app
    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        chunks, size = [], 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect": return
            chunk = message.get("body", b""); size += len(chunk)
            if size > 128_000:
                return await JSONResponse({"detail": "Request too large"}, status_code=413)(scope, receive, send)
            chunks.append(chunk)
            if not message.get("more_body"): break
        delivered = False
        async def bounded_receive():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": b"".join(chunks), "more_body": False}
            return await receive()
        await self.app(scope, bounded_receive, send)


def create_app(factory=SessionFactory):
    app = FastAPI(title="Planner API", version="1.0.0")
    app.add_middleware(BodyLimitMiddleware)
    from app.bootstrap import organizer_dependencies
    app_factory, organizer, identity = organizer_dependencies(factory)
    app.include_router(router_for(app_factory, organizer, identity))
    from app.application.accounts import AccountService
    from app.infrastructure.gateways import AccountSecurityAdapter
    app.include_router(account_router_for(app_factory, AccountService(settings, AccountSecurityAdapter(), identity)))
    from app.application.dataset import DatasetService
    app.include_router(dataset_router_for(app_factory, DatasetService(), identity))

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, error: RequestValidationError):
        return JSONResponse({"detail": [{"loc": item["loc"], "type": item["type"], "msg": item["msg"]} for item in error.errors()]}, status_code=422)

    @app.exception_handler(ApplicationError)
    async def application_error(request: Request, error: ApplicationError):
        return JSONResponse({"detail": error.detail}, status_code=error.status, headers=error.headers)

    @app.exception_handler(ValueError)
    async def invalid_data(request: Request, error: ValueError):
        return JSONResponse({"detail": "Invalid structured data"}, status_code=422)

    @app.exception_handler(IntegrityError)
    async def conflict(request: Request, error: IntegrityError):
        return JSONResponse({"detail": "Concurrent or conflicting mutation; retry the same operation"}, status_code=409)

    @app.get("/health")
    def health():
        try:
            with factory() as db: db.execute(text("SELECT 1"))
        except Exception:
            return JSONResponse({"status": "database_unavailable"}, status_code=503)
        return {"status": "ok", "aiConfigured": settings.ai_available}

    @app.get("/ready")
    def ready():
        try:
            since = (datetime.now(timezone.utc) - timedelta(seconds=45)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
            with factory() as db: worker = db.scalar(select(ServiceHeartbeat).where(ServiceHeartbeat.seen_at >= since).limit(1))
            if worker is None: return JSONResponse({"status": "worker_unavailable"}, status_code=503)
        except Exception: return JSONResponse({"status": "database_unavailable"}, status_code=503)
        return {"status": "ready", "aiConfigured": settings.ai_available}
    return app


app = create_app()
