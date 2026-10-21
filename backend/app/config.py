from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str = "postgresql+psycopg://planner:planner@127.0.0.1:5432/planner"
    llm_api_key: str = ""
    llm_model: str = ""
    llm_base_url: str = "https://api.openai.com/v1"
    llm_timeout_seconds: float = 30
    worker_concurrency: int = 2
    max_job_attempts: int = 5

    @property
    def ai_available(self) -> bool:
        return bool(self.llm_api_key and self.llm_model)


settings = Settings()
