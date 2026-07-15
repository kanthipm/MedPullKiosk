"""Groq chat-completions client (OpenAI-compatible API, free tier).

The free tier enforces a tokens-per-minute budget (12k TPM on the 70B model),
so 429s are normal during bursts — the client honors Groq's retry-after and
paces itself instead of failing over to the deterministic renderer.
"""

import json
import re
import time

import httpx

from app.config import settings
from app.llm.provider import LLMError

BASE_URL = "https://api.groq.com/openai/v1"
TIMEOUT = 25.0
RETRIABLE = {429, 500, 502, 503, 504}
MAX_ATTEMPTS = 4
MAX_TOTAL_WAIT = 65.0  # enough to roll into the next TPM window


def _retry_after_seconds(response: httpx.Response) -> float:
    header = response.headers.get("retry-after")
    if header:
        try:
            return float(header)
        except ValueError:
            pass
    # Groq also embeds "Please try again in 7.66s" / "in 350ms" in the body.
    match = re.search(r"try again in ([\d.]+)(ms|s)", response.text)
    if match:
        value = float(match.group(1))
        return value / 1000.0 if match.group(2) == "ms" else value
    return 5.0


def complete_json(system: str, user: str, temperature: float = 0.45) -> dict:
    if not settings.groq_api_key:
        raise LLMError("No GROQ_API_KEY configured")

    body = {
        "model": settings.groq_model,
        "temperature": temperature,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    headers = {"Authorization": f"Bearer {settings.groq_api_key}"}

    last_error: Exception | str | None = None
    waited = 0.0
    for attempt in range(MAX_ATTEMPTS):
        try:
            response = httpx.post(
                f"{BASE_URL}/chat/completions", json=body, headers=headers, timeout=TIMEOUT
            )
            if response.status_code in RETRIABLE:
                last_error = f"Groq {response.status_code}: {response.text[:160]}"
                pause = min(_retry_after_seconds(response) + 0.5, MAX_TOTAL_WAIT - waited)
                if attempt == MAX_ATTEMPTS - 1 or pause <= 0:
                    break
                time.sleep(pause)
                waited += pause
                continue
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return json.loads(content)
        except (httpx.HTTPError, json.JSONDecodeError, KeyError, IndexError) as e:
            last_error = e
            if attempt < MAX_ATTEMPTS - 1 and waited < MAX_TOTAL_WAIT:
                time.sleep(1.5 * (attempt + 1))
                waited += 1.5 * (attempt + 1)
    raise LLMError(f"Groq call failed: {last_error}")
