from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parent.parent
PROJECT_DIR = BACKEND_DIR.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(PROJECT_DIR / ".env", BACKEND_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    groq_api_key: str = ""
    # llama-3.3-70b-versatile was retired from Groq (404 model_not_found, Sep 2026)
    groq_model: str = "openai/gpt-oss-120b"
    # Webhook signing secrets — empty means that provider's deliveries are
    # rejected (verification fails closed; the mock/demo path needs none).
    terra_signing_secret: str = ""
    junction_webhook_secret: str = ""
    # Local Ollama is OPT-IN (cloud-first product direction): leave the URL
    # empty and the chain is Groq -> deterministic fallback. Set OLLAMA_URL
    # explicitly to use a local model as the middle tier.
    ollama_url: str = ""
    ollama_model: str = "qwen3-vl-agent:latest"
    database_url: str = f"sqlite:///{BACKEND_DIR / 'data' / 'recovery.db'}"


settings = Settings()
