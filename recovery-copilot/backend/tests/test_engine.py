"""Unit tests for the deterministic analytics, on synthetic series."""

import numpy as np
import pandas as pd

from app.engine.baseline import compute_baseline
from app.engine.composite import composite_index
from app.engine.curves import expected_curve
from app.engine.deviation import analyze_metric, ewma_cusum
from app.engine.trajectory import compare
from app.engine.types import DeviationResult
from app.models.enums import MetricType as M
from app.models.enums import ProcedureType, RiskLevel, TrajectoryState


def _series(pre: list[float], post: list[float]) -> pd.Series:
    index = list(range(-len(pre), 0)) + list(range(len(post)))
    return pd.Series(pre + post, index=index, dtype=float)


def test_ewma_flags_rhr_ramp():
    rng = np.random.default_rng(1)
    pre = list(62 + rng.normal(0, 1.0, 10))
    post = list(62 + rng.normal(0, 1.0, 6)) + [64.5, 67.0, 70.0]
    baseline, dev = analyze_metric(M.RESTING_HR, _series(pre, post), ProcedureType.TKA)
    assert dev.flagged
    assert dev.direction == "up"


def test_ewma_ignores_stable_vitals():
    rng = np.random.default_rng(2)
    pre = list(62 + rng.normal(0, 1.0, 10))
    post = list(62 + rng.normal(0, 1.0, 12))
    _, dev = analyze_metric(M.RESTING_HR, _series(pre, post), ProcedureType.TKA)
    assert not dev.flagged
    assert not dev.drifting


def test_favorable_direction_never_flags():
    # steps well ABOVE the expected curve: adverse only when below
    pre = [8000.0] * 10
    curve = expected_curve(ProcedureType.MENISCUS, np.arange(0, 12))
    post = [8000 * point["mid"] * 1.3 for point in curve]
    _, dev = analyze_metric(M.STEPS, _series(pre, post), ProcedureType.MENISCUS)
    assert not dev.flagged


def test_cusum_catches_slow_drift():
    z = pd.Series([-(0.4 + 0.35 * i) for i in range(9)], index=range(2, 11))
    result = ewma_cusum(M.STEPS, z)
    assert result.drifting


def test_expected_curve_monotonic_and_bounded():
    for procedure in ProcedureType:
        curve = expected_curve(procedure, np.arange(0, 60))
        mids = [point["mid"] for point in curve]
        assert all(b >= a for a, b in zip(mids, mids[1:]))
        assert 0.0 < mids[0] < 1.0 and mids[-1] <= 1.0


def test_trajectory_behind_for_sustained_deficit():
    days = np.arange(0, 15)
    curve = expected_curve(ProcedureType.THA, days)
    index = pd.Series([point["mid"] * 0.82 for point in curve], index=days)
    result = compare(index, ProcedureType.THA, 14)
    assert result.state == TrajectoryState.BEHIND
    assert result.pct is not None and result.pct <= -12


def test_trajectory_unknown_with_thin_history():
    index = pd.Series([0.3, 0.32], index=[0, 1])
    result = compare(index, ProcedureType.THA, 1)
    assert result.state == TrajectoryState.UNKNOWN


def test_change_point_on_residual_not_normal_recovery():
    days = np.arange(0, 40)
    curve = expected_curve(ProcedureType.TKA, days)
    on_track = pd.Series([point["mid"] * 1.02 for point in curve], index=days)
    assert compare(on_track, ProcedureType.TKA, 39).change_point_day is None

    # deficit appearing at day 25
    values = [point["mid"] * (1.0 if point["day"] < 25 else 0.72) for point in curve]
    shifted = pd.Series(values, index=days)
    change_day = compare(shifted, ProcedureType.TKA, 39).change_point_day
    assert change_day is not None and 23 <= change_day <= 28


def _dev(metric: M, raw_z: float) -> DeviationResult:
    return DeviationResult(
        metric_type=str(metric), flagged=False, direction="up" if raw_z > 0 else "down",
        latest_z=raw_z, raw_z=raw_z, consecutive_out=0, drifting=False,
    )


def test_composite_high_for_coupled_deterioration():
    deviations = {
        str(M.RESTING_HR): _dev(M.RESTING_HR, 4.0),
        str(M.SKIN_TEMP): _dev(M.SKIN_TEMP, 4.0),
        str(M.HRV_RMSSD): _dev(M.HRV_RMSSD, -3.0),
        str(M.STEPS): _dev(M.STEPS, -3.5),
        str(M.RESPIRATORY_RATE): _dev(M.RESPIRATORY_RATE, 0.5),
    }
    result = composite_index(deviations)
    assert result.level == "high"
    assert result.drivers[0]["metric_type"] in (str(M.RESTING_HR), str(M.SKIN_TEMP))


def test_composite_normal_for_noise():
    deviations = {
        str(M.RESTING_HR): _dev(M.RESTING_HR, 0.8),
        str(M.SKIN_TEMP): _dev(M.SKIN_TEMP, -0.4),
        str(M.HRV_RMSSD): _dev(M.HRV_RMSSD, 0.6),
    }
    assert composite_index(deviations).level == "normal"


def test_baseline_falls_back_without_preop_data():
    series = pd.Series([70.0, 68.0, 62.0, 63.0, 61.5, 62.5], index=[0, 1, 2, 3, 4, 5])
    baseline = compute_baseline(str(M.RESTING_HR), series)
    assert baseline is not None
    assert "no pre-op" in baseline.window
    # the acute perturbation on days 0-1 must not anchor the baseline
    assert baseline.mean < 65


def test_webhook_unknown_patient_rejected_without_orphans(client, db):
    from sqlalchemy import func, select

    from app.models import Observation

    before = db.scalar(select(func.count(Observation.id)))
    response = client.post(
        "/api/webhooks/wearables/mock",
        json={
            "patient_id": "ghost",
            "provider": "fitbit",
            "records": [
                {"metric_type": "steps", "date": "2030-06-01", "value": 5000.0, "unit": "count"}
            ],
        },
    )
    assert response.status_code == 422
    after = db.scalar(select(func.count(Observation.id)))
    assert after == before  # nothing committed for the unknown patient


def test_golden_tiers(db):
    from app.engine.pipeline import latest_assessment

    expected = {
        "marcus": RiskLevel.HIGH,
        "priya": RiskLevel.MISSING_DATA,
        "linda": RiskLevel.MEDIUM,
        "robert": RiskLevel.MEDIUM,
        "sofia": RiskLevel.MEDIUM,
        "aisha": RiskLevel.MEDIUM,
        "grace": RiskLevel.LOW,
        "david": RiskLevel.LOW,
        "james": RiskLevel.LOW,
        "elena": RiskLevel.LOW,
    }
    for patient_id, tier in expected.items():
        assessment = latest_assessment(db, patient_id)
        assert assessment is not None, patient_id
        assert assessment.risk_level == tier, (
            f"{patient_id}: got {assessment.risk_level}, want {tier}"
        )


def test_marcus_reasons_tell_the_story(db):
    from app.engine.pipeline import latest_assessment

    codes = {r["code"] for r in latest_assessment(db, "marcus").reasons}
    assert {"RHR_RISING", "TEMP_RISING", "COMPOSITE_HIGH"} <= codes
