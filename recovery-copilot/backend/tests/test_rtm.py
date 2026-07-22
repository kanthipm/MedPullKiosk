"""RTM compliance engine + API contracts (SPEC.md §§1, 6, 7, 8, 9).

Readiness stages are pinned per seeded patient (see seed/rtm.py's roster
story). Mutation tests live at the bottom of the file and only touch state
they assert as deltas, because the seeded DB is session-scoped.
"""

from datetime import date

from app.models.enums import GUARDRAIL_SENTENCE
from app.models.patient import Patient
from app.rtm.readiness import compute_readiness


def _readiness(db, patient_id: str) -> dict:
    patient = db.get(Patient, patient_id)
    return compute_readiness(db, patient, date.today())


# --- golden readiness stages -------------------------------------------------

def test_marcus_needs_interactive_call(db):
    """The spec's own showcase: enrolled, 14 minutes, no live interaction."""
    r = _readiness(db, "marcus")
    assert r["enrollment"]["complete"] is True
    assert r["treatment_management"]["minutes"] == 14
    assert r["treatment_management"]["interactive_communication"] is False
    assert r["ready_to_bill"] is False
    # the call is provider-actionable NOW; monitoring days accrue on their own
    assert "call" in r["suggested_action"].lower()


def test_monitoring_days_start_at_enrollment(db):
    """Pre-enrollment device wear builds engine baselines but never counts
    toward the 16-of-30 monitoring threshold. Marcus enrolled 8 days ago, so
    despite ~3 weeks of device data his monitoring count is at most 9 days."""
    r = _readiness(db, "marcus")
    assert r["monitoring"]["enrolled"] is True
    assert 0 < r["monitoring"]["days"] <= 9
    assert r["monitoring"]["eligible"] is False


def test_unenrolled_patients_accrue_no_monitoring_days(db):
    for patient_id in ("grace", "robert"):
        r = _readiness(db, patient_id)
        assert r["monitoring"]["enrolled"] is False
        assert r["monitoring"]["days"] == 0
        monitoring_codes = {e["cpt"]: e for e in r["billing"]}
        assert monitoring_codes["98985"]["eligible"] is False
        assert "enrollment" in monitoring_codes["98985"]["note"].lower()


def test_grace_consent_pending(db):
    r = _readiness(db, "grace")
    assert r["enrollment"]["education_complete"] is True
    assert r["enrollment"]["consent_complete"] is False
    assert r["enrollment"]["complete"] is False
    assert "consent" in r["suggested_action"].lower()


def test_robert_baseline_pending(db):
    r = _readiness(db, "robert")
    assert r["enrollment"]["consent_complete"] is True
    assert r["enrollment"]["baseline_complete"] is False
    assert "baseline" in r["suggested_action"].lower()


def test_david_ready_to_bill(db):
    """Enrolled, ≥16 monitoring days, 45 min incl. call, docs approved."""
    r = _readiness(db, "david")
    assert r["enrollment"]["complete"] is True
    assert r["monitoring"]["eligible"] is True
    assert r["treatment_management"]["minutes"] == 45
    assert r["treatment_management"]["interactive_communication"] is True
    assert r["documentation_ready"] is True
    assert r["ready_to_bill"] is True
    assert "ready to bill" in r["suggested_action"].lower()
    cpts = {e["cpt"] for e in r["billing"] if e["eligible"]}
    assert {"98975", "98977", "98980", "98981"} <= cpts


def test_billing_never_mixes_tiers(db):
    """98980 eligible implies no 98979 row; 98981 units are the 20-min excess."""
    r = _readiness(db, "david")
    eligible = {e["cpt"]: e for e in r["billing"] if e["eligible"]}
    assert "98979" not in eligible
    assert eligible["98981"]["units"] == (45 - 20) // 20


def test_elena_partial_treatment_tier(db):
    """12 min with a call → 98979 territory, not yet 98980."""
    r = _readiness(db, "elena")
    assert r["treatment_management"]["minutes"] == 12
    assert r["treatment_management"]["interactive_communication"] is True
    eligible = {e["cpt"] for e in r["billing"] if e["eligible"]}
    assert "98979" in eligible
    assert "98980" not in eligible


# --- API contracts -----------------------------------------------------------

def test_rtm_endpoint_contract(client):
    body = client.get("/api/patients/marcus/rtm").json()
    for key in (
        "enrollment", "monitoring", "treatment_management", "documentation_ready",
        "billing", "ready_to_bill", "suggested_action", "recent_interactions",
    ):
        assert key in body
    assert body["monitoring"]["target"] == 16
    assert body["monitoring"]["window_days"] == 30


def test_patient_detail_rtm_block_unchanged(client):
    """The pre-P1 rtm block stays shape-compatible (window_days pinned)."""
    body = client.get("/api/patients/marcus").json()
    assert body["rtm"]["window_days"] == 30
    assert "days_with_data" in body["rtm"]


def test_practice_overview(client):
    body = client.get("/api/practice/overview").json()
    assert body["rtm_patients"] == 10
    assert body["ready_to_bill"] >= 2  # david + james seeded ready
    assert body["needs_review"] >= 1
    assert body["estimated_revenue"] > 0


def test_documents_generated_with_guardrail(client):
    """Fallback provider (tests force it) still yields valid, guarded docs."""
    body = client.get("/api/patients/linda/rtm/documents").json()
    kinds = {d["kind"] for d in body["documents"]}
    assert kinds == {"encounter_note", "monthly_summary"}
    for doc in body["documents"]:
        assert doc["status"] == "draft"
        assert doc["body"].endswith(GUARDRAIL_SENTENCE)
        lowered = doc["body"].replace(GUARDRAIL_SENTENCE, "").lower()
        assert "detect" not in lowered and "diagnos" not in lowered


def test_approved_documents_survive(client):
    """Seeded approved docs are returned as-is, never regenerated."""
    body = client.get("/api/patients/david/rtm/documents").json()
    assert all(d["status"] == "approved" for d in body["documents"])
    assert all(d["approved_at"] is not None for d in body["documents"])


# --- mutations (deltas only; keep these last) --------------------------------

def test_call_logs_time_and_interaction(client):
    before = client.get("/api/patients/linda/rtm").json()
    resp = client.post(
        "/api/patients/linda/actions/call", json={"minutes": 6, "note": "Test call"}
    )
    assert resp.status_code == 200
    after = client.get("/api/patients/linda/rtm").json()
    assert after["treatment_management"]["minutes"] == (
        before["treatment_management"]["minutes"] + 6
    )
    assert after["treatment_management"]["interactive_communication"] is True
    assert after["recent_interactions"][0]["kind"] == "call"


def test_review_time_batches(client):
    before = client.get("/api/patients/linda/rtm").json()["treatment_management"]["minutes"]
    resp = client.post("/api/patients/linda/rtm/review-time", json={"seconds": 120})
    assert resp.json()["logged_seconds"] == 120
    after = client.get("/api/patients/linda/rtm").json()["treatment_management"]["minutes"]
    assert after == before + 2
    # sub-15s pings are ignored, not logged
    resp = client.post("/api/patients/linda/rtm/review-time", json={"seconds": 5})
    assert resp.json()["logged_seconds"] == 0


def test_schedule_followup_requires_time(client):
    resp = client.post(
        "/api/patients/linda/actions/schedule-followup", json={"when": "  "}
    )
    assert resp.status_code == 422


def test_approve_then_regenerate_conflict(client):
    docs = client.get("/api/patients/sofia/rtm/documents").json()["documents"]
    note = next(d for d in docs if d["kind"] == "encounter_note")
    resp = client.post(f"/api/patients/sofia/rtm/documents/{note['id']}/approve")
    assert resp.json()["status"] == "approved"
    # approved documentation is signed — regeneration must refuse
    resp = client.post(f"/api/patients/sofia/rtm/documents/{note['id']}/regenerate")
    assert resp.status_code == 409
    # and sofia's documentation requirement is now satisfied
    assert client.get("/api/patients/sofia/rtm").json()["documentation_ready"] is True
