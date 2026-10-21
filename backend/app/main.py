from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from app.api.routes import router_for
from app.config import settings
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
    app.include_router(router_for(factory))

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
    return app


app = create_app()
