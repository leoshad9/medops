from pydantic_settings import BaseSettings, SettingsConfigDict

# API dialects (wire protocol), not vendor product names.
PROVIDER_GENERATE_CONTENT = "generate_content"
PROVIDER_CHAT_COMPLETIONS = "chat_completions"


class Settings(BaseSettings):
    """Env-backed config. Secrets never go in source control."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "MedOps AI Service"

    # LLM_PROVIDER: generate_content | chat_completions
    llm_provider: str = ""
    llm_api_key: str = ""
    llm_model: str = ""
    llm_base_url: str = ""

    llm_temperature: float = 0.2
    llm_max_tokens: int = 1200
    request_timeout_seconds: float = 20.0
    llm_max_attempts: int = 3
    llm_retry_backoff_seconds: float = 1.0
    max_pdf_chars: int = 12_000

    def resolved_provider(self) -> str | None:
        if not self.llm_api_key.strip():
            return None
        raw = self.llm_provider.strip().lower().replace("-", "_")
        if raw in ("", PROVIDER_GENERATE_CONTENT):
            return PROVIDER_GENERATE_CONTENT
        if raw == PROVIDER_CHAT_COMPLETIONS:
            return PROVIDER_CHAT_COMPLETIONS
        raise ValueError(
            f"Unsupported LLM_PROVIDER={self.llm_provider!r}; "
            f"use '{PROVIDER_GENERATE_CONTENT}' or '{PROVIDER_CHAT_COMPLETIONS}'"
        )

    @property
    def llm_enabled(self) -> bool:
        try:
            return self.resolved_provider() is not None
        except ValueError:
            return False

    @property
    def active_model(self) -> str | None:
        if self.resolved_provider() is None:
            return None
        model = self.llm_model.strip()
        if not model:
            raise ValueError("LLM_MODEL is required when LLM_API_KEY is set")
        return model

    @property
    def active_base_url(self) -> str | None:
        if self.resolved_provider() is None:
            return None
        base = self.llm_base_url.strip()
        if not base:
            raise ValueError("LLM_BASE_URL is required when LLM_API_KEY is set")
        return base


settings = Settings()
