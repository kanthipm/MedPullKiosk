"""The orthopedic recovery measures — the pure rules on synthetic inputs, the
seeded roster's stories, and the API contract.

The rules are pinned to the published thresholds they cite: week-1 drainage
never alerts on its own, week-2 drainage does, a TKA rebound-window pain value
does not fire where the same value on day 20 would, and a flexion curve that
projects under 90° at week 6 fires inside the day 21–35 runway."""

from datetime import date

from app.engine.ortho_measures import (
    RomPoint,
    load_pain_sensitivity,
    nocturnal_disruption,
    pain_trajectory,
    range_of_motion,
    wound_drainage,
)
from app.models.enums import MetricStatus, ProcedureType
from app.models.enums import MetricType as M
from app.seed.ortho import generate_ortho_observations
from app.seed.patients import PATIENTS, get_spec
from app.seed.scenarios import get_scenario

SURGERY = date(2026, 1, 1)


# --- wound ladder ------------------------------------------------------------


def test_week1_drainage_alone_never_alerts():
    # a wet dressing on days 3-5 is the ICM persistent-drainage definition: monitor, not call
    grades = {0: 2, 1: 2, 2: 0, 3: 2, 4: 2, 5: 2}
    m = wound_drainage(5, grades, SURGERY)
    assert m.status is MetricStatus.WATCH
    # light drainage on a single first-week day is expected and never alerts
    grades = {0: 2, 1: 2, 2: 1, 3: 0, 4: 0, 5: 1}
    m = wound_drainage(5, grades, SURGERY)
    assert m.status is MetricStatus.OK
    assert m.status_text == "Expected in week 1"


def test_persistent_week1_drainage_past_five_days_contacts():
    grades = {d: 2 for d in range(0, 7)}
    m = wound_drainage(6, grades, SURGERY)
    assert m.status is MetricStatus.FLAG and m.status_text == "Contact today"


def test_any_week2_drainage_contacts_and_moderate_escalates():
    dry = {d: 0 for d in range(3, 8)}
    m = wound_drainage(9, {**{0: 2, 1: 1, 2: 1}, **dry, 8: 1, 9: 1}, SURGERY)
    assert m.status is MetricStatus.FLAG
    assert "week 2" in m.finding
    m = wound_drainage(9, {**{0: 2, 1: 1, 2: 1}, 8: 3, 9: 3}, SURGERY)
    assert m.status is MetricStatus.FLAG and m.status_text == "Call today"


def test_dry_through_day_14_reports_the_npv():
    grades = {d: (1 if d <= 2 else 0) for d in range(0, 15)}
    m = wound_drainage(14, grades, SURGERY)
    assert m.status is MetricStatus.OK
    assert "467" in m.finding


def test_wound_never_uses_diagnostic_verbs():
    for grades, day in (({8: 3, 9: 3}, 9), ({16: 4}, 16), ({d: 1 for d in range(0, 12)}, 11)):
        m = wound_drainage(day, grades, SURGERY)
        assert "detect" not in m.finding.lower() and "diagnos" not in m.finding.lower()


# --- pain trajectory ---------------------------------------------------------


def test_tka_rebound_window_does_not_fire_where_day_20_would():
    # the review's own worked example: 6.3 on POD 9 is inside the widened band
    pain = {d: 5.0 for d in range(0, 8)} | {8: 6.3, 9: 6.3}
    m = pain_trajectory(ProcedureType.TKA, 9, pain, SURGERY)
    assert m.status is not MetricStatus.FLAG or m.status_text != "Above expected 2 days"
    # 6.6 on POD 19-20 is well outside the 2.0 band around ~3.8
    pain = {d: 4.0 for d in range(0, 19)} | {19: 6.6, 20: 6.6}
    m = pain_trajectory(ProcedureType.TKA, 20, pain, SURGERY)
    assert m.status is MetricStatus.FLAG


def test_pain_rising_against_a_falling_curve_flags():
    pain = {0: 6.0, 1: 5.8, 2: 5.4, 3: 5.0, 4: 4.9, 5: 5.5, 6: 6.2, 7: 6.8, 8: 7.5}
    m = pain_trajectory(ProcedureType.TKA, 8, pain, SURGERY)
    assert m.status is MetricStatus.FLAG
    assert m.status_text == "Rising 4 days"


def test_pain_no_recent_log_is_nodata():
    m = pain_trajectory(ProcedureType.ACL, 12, {d: 3.0 for d in range(0, 8)}, SURGERY)
    assert m.status is MetricStatus.NODATA


# --- range of motion ---------------------------------------------------------


def _flexion(points: dict[int, float]) -> list[RomPoint]:
    return [RomPoint(d, "flexion", v, "clinic" if d % 7 == 0 else "phone") for d, v in points.items()]


def test_tka_mua_projection_fires_inside_the_runway():
    slow = _flexion({10: 74, 13: 76, 16: 77, 19: 78, 22: 79, 25: 80})
    m = range_of_motion(ProcedureType.TKA, 25, slow, SURGERY)
    assert m.status is MetricStatus.FLAG
    assert "week 6" in m.status_text
    quick = _flexion({10: 92, 13: 97, 16: 101, 19: 104, 22: 107, 25: 110})
    m = range_of_motion(ProcedureType.TKA, 25, quick, SURGERY)
    assert m.status is MetricStatus.OK


def test_acl_extension_deficit_past_week_four_escalates():
    rom = _flexion({14: 100, 21: 112, 28: 118}) + [
        RomPoint(28, "extension_deficit", 8.0, "clinic")
    ]
    m = range_of_motion(ProcedureType.ACL, 28, rom, SURGERY)
    assert m.status is MetricStatus.WATCH
    assert "Extension deficit" in m.status_text


def test_lumbar_rom_is_not_tracked_by_design():
    m = range_of_motion(ProcedureType.LUMBAR, 6, [], SURGERY)
    assert m.status is MetricStatus.NODATA
    assert "not" in m.status_text.lower()


def test_ankle_rom_is_trend_only():
    m = range_of_motion(ProcedureType.ANKLE, 21, _flexion({}) + [
        RomPoint(d, "dorsiflexion", 7.0, "phone") for d in (11, 14, 17, 20)
    ], SURGERY)
    assert m.status is MetricStatus.OK and m.status_text == "Trend only"


# --- load-pain sensitivity ---------------------------------------------------


def test_load_spike_followed_by_pain_is_watched():
    steps = {d: 3000.0 + 120 * d for d in range(0, 13)}
    steps[10] = 7200.0
    pain = {d: max(1.0, 5.0 - 0.3 * d) for d in range(0, 13)}
    pain[11] += 1.6
    m = load_pain_sensitivity(12, steps, pain, SURGERY)
    assert m.status is MetricStatus.WATCH
    assert "day 10" in m.finding


def test_load_without_steps_is_nodata():
    m = load_pain_sensitivity(12, {}, {d: 3.0 for d in range(0, 13)}, SURGERY)
    assert m.status is MetricStatus.NODATA


# --- nocturnal disruption ----------------------------------------------------


def test_fragmented_nights_that_track_evening_pain_flag():
    pre = {d: 1.0 for d in range(-10, 0)}
    post_awake = {d: 1.0 + 0.6 * d for d in range(0, 9)}
    evening = {d: 3.0 + 0.5 * d for d in range(0, 9)}
    m = nocturnal_disruption(8, pre | post_awake, evening, SURGERY)
    assert m.status is MetricStatus.FLAG
    quiet = {d: 1.0 for d in range(-10, 9)}
    m = nocturnal_disruption(8, quiet, evening, SURGERY)
    assert m.status is MetricStatus.OK


# --- seed + API --------------------------------------------------------------


def test_ortho_generator_is_deterministic():
    spec = get_spec("chris")
    a = generate_ortho_observations(spec, get_scenario("chris"), date.today())
    b = generate_ortho_observations(spec, get_scenario("chris"), date.today())
    assert [(o.dedupe_key, o.value_num) for o in a] == [(o.dedupe_key, o.value_num) for o in b]
    kinds = {o.metric_type for o in a}
    assert {M.PAIN_NRS, M.WOUND_DRAINAGE, M.RANGE_OF_MOTION, M.SLEEP_AWAKENINGS} <= kinds


def test_ortho_rows_never_qualify_for_rtm_on_their_own():
    for spec in PATIENTS:
        for o in generate_ortho_observations(spec, get_scenario(spec.id), date.today()):
            assert o.qualifies_for_rtm is False


def test_ortho_endpoint_contract(client):
    body = client.get("/api/patients/marcus/ortho-measures").json()
    keys = [m["key"] for m in body["measures"]]
    assert keys == [
        "pain_trajectory",
        "load_pain_sensitivity",
        "range_of_motion",
        "wound_drainage",
        "nocturnal_disruption",
    ]
    for m in body["measures"]:
        assert m["status"] in ("flag", "watch", "ok", "nodata")
        assert m["evidence"] and m["finding"]
    assert body["provenance"]["developed_with"]
    assert client.get("/api/patients/nobody/ortho-measures").status_code == 404


def test_seeded_stories(client):
    by = lambda pid: {m["key"]: m for m in client.get(f"/api/patients/{pid}/ortho-measures").json()["measures"]}  # noqa: E731
    marcus = by("marcus")
    assert marcus["wound_drainage"]["status"] == "flag"
    assert marcus["pain_trajectory"]["status"] == "flag"
    chris = by("chris")
    assert chris["wound_drainage"]["status"] == "ok"
    assert chris["load_pain_sensitivity"]["status"] == "watch"
    assert chris["load_pain_sensitivity"]["status_text"] == "Overdid it once"
    assert chris["range_of_motion"]["status"] == "ok"
    assert chris["pain_trajectory"]["status"] == "ok"
    marcus_load = marcus["load_pain_sensitivity"]
    assert marcus_load["status"] == "watch" and marcus_load["status_text"] == "Not load-driven"
    robert = by("robert")
    assert robert["range_of_motion"]["status"] == "nodata"


def test_chris_is_the_demo_stand_in(client):
    body = client.get("/api/patients/chris").json()
    assert body["name"] == "Chris Morgan"
    assert body["risk"]["level"] == "low"
    assert body["device"]["provider"] == "apple"
