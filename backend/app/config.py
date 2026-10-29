from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Literal
from pydantic import Field, model_validator


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str = "postgresql+psycopg://planner:planner@127.0.0.1:5432/planner"
    llm_api_key: str = ""
    llm_model: str = ""
    llm_base_url: str = "https://api.openai.com/v1"
    llm_timeout_seconds: float = 30
    llm_response_format: Literal["auto", "json_schema", "json_object"] = "auto"
    worker_concurrency: int = 2
    max_job_attempts: int = 5
    ai_daily_request_limit: int = Field(default=100, ge=1, le=10_000)
    app_env: Literal["development", "production"] = "development"
    public_base_url: str = "http://127.0.0.1:8000"
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_from: str = ""
    smtp_starttls: bool = True
    auth_rate_limit_secret: str = "development-only-change-for-production"
    google_client_id: str = ""
    google_client_secret: str = ""

    @model_validator(mode="after")
    def production_configuration(self):
        if self.app_env == "production":
            from urllib.parse import urlparse
            parsed = urlparse(self.public_base_url)
            if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.query or parsed.fragment: raise ValueError("Production PUBLIC_BASE_URL must be an HTTPS origin")
            if len(self.auth_rate_limit_secret) < 32 or self.auth_rate_limit_secret.startswith(("development", "REPLACE")): raise ValueError("Set a random AUTH_RATE_LIMIT_SECRET for production")
            if not self.smtp_host or not self.smtp_from or not self.smtp_starttls: raise ValueError("Configure verified SMTP with STARTTLS for public accounts")
            if "planner:planner@" in self.database_url or "URL_ENCODED" in self.database_url: raise ValueError("Default/template database credentials are not allowed in production")
        return self

    @property
    def ai_available(self) -> bool:
        return bool(self.llm_api_key and self.llm_model)


settings = Settings()
