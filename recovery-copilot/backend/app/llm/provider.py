"""LLM provider selection and dispatch.

Priority: Groq (when a key is configured) → local Ollama (when reachable and
the configured model is present) → deterministic fallback. The Ollama probe is
cached briefly so worklist rendering doesn't ping the socket per patient.
"""

import logging
import time

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class LLMError(Exception):
    pass


_ollama_probe: dict = {"at": 0.0, "ok": False}
_PROBE_TTL = 60.0


def _ollama_available() -> bool:
    if not settings.ollama_url:
        return False
    now = time.monotonic()
    if now - _ollama_probe["at"] < _PROBE_TTL:
        return _ollama_probe["ok"]
    ok = False
    try:
        response = httpx.get(f"{settings.ollama_url}/api/tags", timeout=2.0)
        response.raise_for_status()
        models = [m.get("name", "") for m in response.json().get("models", [])]
        ok = settings.ollama_model in models
        if not ok and models:
            logger.warning(
                "Ollama is up but model %r is not pulled (available: %s)",
                settings.ollama_model, ", ".join(models[:5]),
            )
    except httpx.HTTPError:
        ok = False
    _ollama_probe.update(at=now, ok=ok)
    return ok


def provider_name() -> str:
    if settings.groq_api_key:
        return "groq"
    if _ollama_available():
        return "ollama"
    return "fallback"


def model_name() -> str | None:
    provider = provider_name()
    if provider == "groq":
        return settings.groq_model
    if provider == "ollama":
        return settings.ollama_model
    return None


def complete_json(
    system: str, user: str, num_predict: int = 700, temperature: float = 0.45
) -> dict:
    """Dispatch one JSON completion to the active real-LLM provider.
    Raises LLMError when none is available or the call fails."""
    provider = provider_name()
    if provider == "groq":
        from app.llm import groq

        return groq.complete_json(system, user, temperature=temperature)
    if provider == "ollama":
        from app.llm import ollama

        return ollama.complete_json(system, user, num_predict=num_predict, temperature=temperature)
    raise LLMError("No LLM provider available")
