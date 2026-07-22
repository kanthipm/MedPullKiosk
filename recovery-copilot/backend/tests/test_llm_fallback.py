import re

from app.llm import insights as insights_mod
from app.llm.insights import BANNED, get_daily_briefing, get_patient_insight
from app.models.enums import GUARDRAIL_SENTENCE, InsightKind


def _narratives(content) -> list[str]:
    if isinstance(content, str):
        return [content]
    if isinstance(content, dict):
        return [s for v in content.values() for s in _narratives(v)]
    if isinstance(content, list):
        return [s for v in content for s in _narratives(v)]
    return []


def test_all_kinds_generate_valid_content(db):
    for kind in (
        InsightKind.WORKLIST_REASON,
        InsightKind.PATIENT_SUMMARY,
        InsightKind.SUGGESTED_ACTIONS,
    ):
        insight = get_patient_insight(db, kind, "marcus")
        assert insight.llm_provider == "fallback"
        assert insight.content
    briefing = get_daily_briefing(db)
    assert "Marcus" in briefing.content["briefing"]


def test_summary_ends_with_guardrail(db):
    for patient_id in ("marcus", "priya", "david"):
        insight = get_patient_insight(db, InsightKind.PATIENT_SUMMARY, patient_id)
        assert insight.content["summary"].endswith(GUARDRAIL_SENTENCE)


def test_no_banned_phrases_anywhere(db):
    for patient_id in ("marcus", "linda", "priya", "david"):
        for kind in (
            InsightKind.WORKLIST_REASON,
            InsightKind.PATIENT_SUMMARY,
            InsightKind.SUGGESTED_ACTIONS,
        ):
            content = get_patient_insight(db, kind, patient_id).content
            for text in _narratives(content):
                scrubbed = text.replace(GUARDRAIL_SENTENCE, "")
                assert not BANNED.search(scrubbed), (patient_id, kind, text)


def test_cache_returns_same_row(db):
    a = get_patient_insight(db, InsightKind.PATIENT_SUMMARY, "marcus")
    b = get_patient_insight(db, InsightKind.PATIENT_SUMMARY, "marcus")
    assert a.id == b.id


def test_diagnostic_llm_output_is_rejected(db, monkeypatch):
    """A provider emitting diagnostic language must be silently replaced."""
    monkeypatch.setattr(insights_mod, "provider_name", lambda: "groq")
    monkeypatch.setattr(
        insights_mod,
        "complete_json",
        lambda system, user, **kw: {"summary": "We detected an infection in the knee. " * 4},
    )
    insight = get_patient_insight(db, InsightKind.PATIENT_SUMMARY, "linda")
    assert insight.llm_provider == "fallback"
    assert "detected" not in insight.content["summary"]
    assert insight.content["summary"].endswith(GUARDRAIL_SENTENCE)


def test_worklist_reason_length_contract(db):
    for patient_id in ("marcus", "linda", "sofia"):
        reason = get_patient_insight(db, InsightKind.WORKLIST_REASON, patient_id).content["reason"]
        assert 0 < len(reason) <= 90


def test_banned_regex_shape():
    assert re.search(BANNED, "Detected a problem")
    assert re.search(BANNED, "possible diagnosis")
    assert not re.search(BANNED, "signals for clinician review")


def test_groq_cooldown_prevents_hammering(monkeypatch):
    """A failed Groq call trips a cooldown so subsequent requests skip its
    retry budget entirely instead of hanging every page load on a dead key."""
    from app.llm import provider

    monkeypatch.setattr(provider.settings, "groq_api_key", "test-key")
    assert provider.provider_name() == "groq"
    provider.note_groq_failure()
    # cooling down: no Ollama in tests, so the deterministic renderer takes over
    assert provider.provider_name() == "fallback"
    provider._groq_cooldown["until"] = 0.0  # expire — Groq is probed again
    assert provider.provider_name() == "groq"
