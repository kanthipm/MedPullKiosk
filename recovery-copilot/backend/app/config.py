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
    groq_model: str = "llama-3.3-70b-versatile"
    # Local Ollama (used when no Groq key is set and the server is reachable) —
    # same local-LLM architecture as the MedPull kiosk's intake copilot.
    ollama_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "qwen3-vl-agent:latest"
    database_url: str = f"sqlite:///{BACKEND_DIR / 'data' / 'recovery.db'}"


settings = Settings()
