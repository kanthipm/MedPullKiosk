"""Warm every narrative through the real LLM, paced under Groq's free tier.

    uv run python -m app.seed.warm        # or: make warm

The seed's own warm-up is one quick pass: it asks for every narrative back to
back, so on the free tier's 8,000 tokens-per-minute budget the first few land
and the rest trip a 429, cool Groq down, and render through the deterministic
fallback. That is the right shape for a page load and the wrong one for a
demo. This pass spaces its calls out, clears the cooldown between them, and
comes back for whatever fell back until every narrative on the roster carries
the model's name — about ten minutes for the full roster.

Run it on the day of a demo, before opening the app: the assessment hash
carries the calendar date, so every narrative regenerates on the first
request of each day.
"""

import sys
import time
from datetime import date

from sqlalchemy.orm import Session

from app.config import settings
from app.database import SessionLocal
from app.engine.pipeline import latest_assessment, run_all
from app.llm.insights import get_daily_briefing, get_patient_insight
from app.llm.provider import provider_name, reset_cooldowns
from app.models.enums import InsightKind, RiskLevel
from app.seed.patients import PATIENTS

# ~2-3k tokens per narrative against an 8k/min budget: three calls a minute.
PACE_S = 21.0
MAX_ROUNDS = 6

BRIEFING = "__briefing__"


def _wanted_from_llm(db: Session, patient_id: str, kind: InsightKind) -> bool:
    """Low-risk worklist reasons are deliberately rules-based (insights.py
    skips the model for them), so a fallback row there is the finished state."""
    if kind is InsightKind.WORKLIST_REASON:
        assessment = latest_assessment(db, patient_id)
        return assessment is None or assessment.risk_level != RiskLevel.LOW
    return True


def warm_narratives(db: Session, pace_s: float = PACE_S, max_rounds: int = MAX_ROUNDS) -> int:
    """Returns how many narratives still render through the fallback."""
    items: list[tuple[str, InsightKind]] = [
        (spec.id, kind)
        for spec in PATIENTS
        for kind in (
            InsightKind.PATIENT_SUMMARY,
            InsightKind.SUGGESTED_ACTIONS,
            InsightKind.WORKLIST_REASON,
        )
    ] + [(BRIEFING, InsightKind.DAILY_BRIEFING)]

    for round_no in range(1, max_rounds + 1):
        pending: list[tuple[str, InsightKind]] = []
        for patient_id, kind in items:
            reset_cooldowns()
            if patient_id == BRIEFING:
                insight = get_daily_briefing(db)
            else:
                insight = get_patient_insight(db, kind, patient_id)
            done = insight.llm_provider != "fallback" or (
                patient_id != BRIEFING and not _wanted_from_llm(db, patient_id, kind)
            )
            mark = "ok " if done else "..."
            print(f"  {mark} {patient_id:<10} {str(kind):<18} {insight.llm_provider}", flush=True)
            if not done:
                pending.append((patient_id, kind))
            time.sleep(pace_s)
        if not pending:
            return 0
        print(f"  round {round_no}: {len(pending)} still on the fallback — going again")
        items = pending
    return len(items)


def main() -> None:
    if not settings.groq_api_key:
        print("GROQ_API_KEY is not set — nothing to warm; narratives stay rules-based.")
        sys.exit(0)
    db = SessionLocal()
    try:
        run_all(db)
        print(f"provider: {provider_name()} ({settings.groq_model}) · {date.today()}")
        remaining = warm_narratives(db)
    finally:
        db.close()
    if remaining:
        print(f"{remaining} narrative(s) still rules-based after {MAX_ROUNDS} rounds.")
        sys.exit(1)
    print("Every narrative on the roster is model-generated.")


if __name__ == "__main__":
    main()
