import html
from urllib.parse import parse_qs, urlencode
from uuid import UUID
from typing import Literal
from fastapi import APIRouter, Body, Depends, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from app.domain.errors import ApplicationError
from pydantic import EmailStr, Field
from app.schemas import DTO

class Credentials(DTO):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)
    deviceId: UUID

class Registration(DTO):
    email: EmailStr
    password: str = Field(min_length=12, max_length=128)

class EmailRequest(DTO):
    email: EmailStr

class LinkToken(DTO):
    token: str = Field(min_length=20, max_length=200, pattern=r"^[A-Za-z0-9_-]+$")

class ResetPassword(LinkToken):
    password: str = Field(min_length=12, max_length=128)

class GoogleStart(DTO):
    challenge: str = Field(min_length=43, max_length=43, pattern=r"^[A-Za-z0-9_-]+$")
    password: str | None = Field(default=None, max_length=128)

class GoogleExchange(DTO):
    state: str = Field(min_length=20, max_length=100)
    code: str = Field(min_length=20, max_length=100)
    verifier: str = Field(min_length=43, max_length=128, pattern=r"^[A-Za-z0-9._~-]+$")
    deviceId: UUID

class DeleteAccount(DTO):
    ownerId: UUID
    confirmation: Literal["DELETE"]
    password: str = Field(min_length=1, max_length=128)

def browser_form(token, reset=False):
    LinkToken(token=token)
    title = "Choose a new Planner password" if reset else "Confirm your Planner email"
    password = '<label>New password (at least 12 characters)<input name="password" type="password" minlength="12" maxlength="128" required autocomplete="new-password"></label>' if reset else ""
    action = "/api/v1/auth/password-reset" if reset else "/api/v1/auth/verify-email/confirm"
    body = f'<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>{title}</title><h1>{title}</h1><form method="post" action="{action}"><input type="hidden" name="token" value="{html.escape(token, quote=True)}">{password}<button type="submit">Continue</button></form></html>'
    return HTMLResponse(body, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "Content-Security-Policy": "default-src 'none'; form-action 'self'; frame-ancestors 'none'"})

def router_for(factory, service):
    router = APIRouter(prefix="/api/v1/auth")
    def session():
        with factory() as db: yield db
    def limited(request, db, action, limit=10):
        service.rate_limit(db, request.client.host if request.client else "unknown", action, limit)

    @router.get("/options")
    def options():
        return service.options()

    @router.post("/register", status_code=202)
    def register(body: Registration, request: Request, db=Depends(session)):
        limited(request, db, "register", 5)
        return service.register(db, str(body.email).casefold(), body.password)

    @router.post("/login")
    def login(body: Credentials, request: Request, db=Depends(session)):
        limited(request, db, "login")
        return service.login(db, str(body.email).casefold(), body.password, body.deviceId)

    @router.post("/forgot-password", status_code=202)
    def forgot(body: EmailRequest, request: Request, db=Depends(session)):
        limited(request, db, "forgot", 5)
        return service.forgot(db, str(body.email).casefold())

    @router.post("/verify-email")
    def verify(body: LinkToken, request: Request, db=Depends(session)):
        limited(request, db, "verify")
        return service.verify_email(db, body.token)

    @router.post("/reset-password")
    def reset(body: ResetPassword, request: Request, db=Depends(session)):
        limited(request, db, "reset")
        return service.reset_password(db, body.token, body.password)

    @router.get("/verify-email", response_class=HTMLResponse)
    def verify_form(token: str): return browser_form(token)

    @router.post("/verify-email/confirm", response_class=HTMLResponse, include_in_schema=False)
    def verify_form_post(request: Request, raw: bytes = Body(media_type="application/x-www-form-urlencoded"), db=Depends(session)):
        limited(request, db, "verify")
        body = LinkToken.model_validate({key: values[0] for key, values in parse_qs(raw.decode(), max_num_fields=2).items()})
        service.verify_email(db, body.token)
        return HTMLResponse("<p>Email confirmed. Return to Planner and sign in.</p>", headers={"Cache-Control": "no-store"})

    @router.get("/password-reset", response_class=HTMLResponse)
    def reset_form(token: str): return browser_form(token, True)

    @router.post("/password-reset", response_class=HTMLResponse, include_in_schema=False)
    def reset_form_post(request: Request, raw: bytes = Body(media_type="application/x-www-form-urlencoded"), db=Depends(session)):
        limited(request, db, "reset")
        body = ResetPassword.model_validate({key: values[0] for key, values in parse_qs(raw.decode(), max_num_fields=3).items()})
        service.reset_password(db, body.token, body.password)
        return HTMLResponse("<p>Password changed. Return to Planner and sign in again.</p>", headers={"Cache-Control": "no-store"})

    @router.post("/google/start")
    def google_start(body: GoogleStart, request: Request, authorization: str | None = Header(default=None), db=Depends(session)):
        limited(request, db, "google-start")
        return service.google_start(db, body.challenge, authorization, body.password)

    @router.get("/google/callback")
    def google_callback(state: str, request: Request, code: str | None = None, error: str | None = None, db=Depends(session)):
        limited(request, db, "google-callback", 20)
        try:
            if error or not code: raise HTTPException(400, "Google sign-in was cancelled. You can try again in Planner.")
            return RedirectResponse(service.google_callback(db, state, code), status_code=303, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"})
        except (HTTPException, ApplicationError) as failure:
            link = "app.planner://auth?" + urlencode({"state": state[:100], "error": "signin_failed"})
            return HTMLResponse(f'<h1>Sign-in not completed</h1><p>{html.escape(str(failure.detail))}</p><a href="{html.escape(link, quote=True)}">Return to Planner</a>', status_code=failure.status_code if isinstance(failure, HTTPException) else failure.status, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'"})

    @router.post("/google/exchange")
    def google_exchange(body: GoogleExchange, request: Request, db=Depends(session)):
        limited(request, db, "google-exchange")
        return service.google_exchange(db, body.state, body.code, body.verifier, body.deviceId)

    @router.delete("/account")
    def delete_account(body: DeleteAccount, request: Request, authorization: str | None = Header(default=None), db=Depends(session)):
        limited(request, db, "delete", 5)
        return service.delete_account(db, body.ownerId, body.password, authorization)
    return router
