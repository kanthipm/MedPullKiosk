"""Orthopedic recovery measures — five procedure-specific readouts that sit
between the patient's check-ins and the wearable signal cards.

Provenance. The measures come from two places, and each card says which:

* the MedPull Ortho Metrics & Task Library — the metric set drafted with the
  practice's orthopedic surgeons and physical therapists (M2 load–pain
  sensitivity, M9 nocturnal disruption, M17/M18 milestone and change-point);
* the clinical content review (docs/backend-design/04-clinical-content.md),
  which carries the published thresholds: the week-conditional wound
  drainage ladder (Wouthuyzen-Bakker et al. 2023, n=1,019), the TKA/THA pain
  anchors with the POD 8–12 rebound band, the ROM gates and the week-6 MUA
  projection, and the sleep-fragmentation-not-duration result (Gibian 2023).

Boundary. These are deterministic readouts for the care team's review. They
do not enter the risk tier (engine/risk.py), and they never name a
complication as present: the subject of a wound flag is the drainage
pattern, the subject of a pain flag is the pain curve. Ordinal and sparse
inputs are scored by rules, never z-scores — a z-score on a drainage grade
is meaningless.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timedelta
from typing import Any

import numpy as np
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.enums import GUARDRAIL_SENTENCE, MetricStatus, ProcedureType
from app.models.enums import MetricType as M
from app.models.observation import Observation
from app.models.patient import Patient

SERIES_DAYS = 14
RECENT_DAYS = 3  # a stream silent this long carries no verdict

PROVENANCE = {
    "developed_with": (
        "Orthopedic surgeons and physical therapists — the MedPull Ortho Metrics "
        "& Task Library (M2, M9, M17/M18) and the clinical content review"
    ),
    "evidence_base": (
        "Thresholds from peer-reviewed cohorts, cited on each measure; consensus "
        "milestones are labelled provisional"
    ),
    "boundary": (
        "Informs the care team's review and does not set the risk tier. "
        + GUARDRAIL_SENTENCE
    ),
}


@dataclass
class OrthoMeasure:
    key: str
    name: str
    family: str
    source: str  # patient_reported | clinician_entered | derived
    source_label: str
    status: MetricStatus
    status_text: str
    value: str | None
    unit: str
    delta: str | None
    finding: str
    next_step: str | None
    evidence: str
    coverage_text: str
    guarded: bool
    reference: float | None
    series_unit: str
    series: list[dict[str, Any]] = field(default_factory=list)


@dataclass(frozen=True)
class RomPoint:
    day: int
    motion: str
    degrees: float
    measured_by: str


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------


def _interp(anchors: list[tuple[int, float]], day: float) -> float:
    xs = [a[0] for a in anchors]
    ys = [a[1] for a in anchors]
    return float(np.interp(day, xs, ys))


def _median2(points: dict[int, float], day: int) -> float | None:
    """Two-day median (mean of the pair) to kill single-day noise; falls back
    to the one day present."""
    vals = [points[d] for d in (day - 1, day) if d in points]
    return float(np.mean(vals)) if vals else None


def _theil_sen(points: list[tuple[int, float]]) -> tuple[float, float] | None:
    """Median of pairwise slopes, median intercept. Ten lines of numpy; scipy
    is excluded from the Lambda artifact."""
    if len(points) < 3:
        return None
    slopes = [
        (y2 - y1) / (x2 - x1)
        for i, (x1, y1) in enumerate(points)
        for (x2, y2) in points[i + 1 :]
        if x2 != x1
    ]
    if not slopes:
        return None
    slope = float(np.median(slopes))
    intercept = float(np.median([y - slope * x for x, y in points]))
    return slope, intercept


def _pearson(xs: list[float], ys: list[float]) -> float | None:
    if len(xs) < 6 or np.std(xs) == 0 or np.std(ys) == 0:
        return None
    return float(np.corrcoef(xs, ys)[0, 1])


def _series(
    points: dict[int, float], surgery_date: date, postop_day: int, digits: int = 1
) -> list[dict[str, Any]]:
    return [
        {"date": (surgery_date + timedelta(days=d)).isoformat(), "value": round(v, digits)}
        for d, v in sorted(points.items())
        if postop_day - SERIES_DAYS < d <= postop_day
    ]


def _coverage(points: dict[int, float], postop_day: int, window: int = 7, noun: str = "days") -> str:
    n = len([d for d in points if postop_day - window < d <= postop_day])
    return f"{min(n, window)} of {window} {noun} logged"


def _signed(v: float, digits: int = 1) -> str:
    return f"{v:+.{digits}f}"


def _week(day: int) -> int:
    return max(1, (day + 6) // 7) if day > 0 else 1


# ---------------------------------------------------------------------------
# 1. Pain trajectory
# ---------------------------------------------------------------------------

# Published anchors (POD, mean NRS): TKA 5.8 / 4.6 / 3.0 at POD 1 / 8 / 29, THA
# 3.1 / 2.3 at POD 1 / 8 (n=103 per procedure, one centre). The POD 9 rebound
# value is provisional; other procedures carry a consensus shape.
PAIN_ANCHORS: dict[ProcedureType, list[tuple[int, float]]] = {
    ProcedureType.TKA: [(1, 5.8), (8, 4.6), (9, 4.8), (29, 3.0), (60, 2.0)],
    ProcedureType.THA: [(1, 3.1), (8, 2.3), (9, 2.6), (12, 2.3), (29, 1.5), (60, 1.0)],
    ProcedureType.ACL: [(1, 5.5), (7, 3.5), (14, 2.5), (28, 1.5), (60, 1.0)],
    ProcedureType.ROTATOR_CUFF: [(1, 6.0), (7, 4.5), (14, 3.5), (28, 2.5), (60, 1.5)],
    ProcedureType.LUMBAR: [(1, 5.5), (7, 4.0), (14, 3.0), (28, 2.5), (60, 2.0)],
    ProcedureType.ANKLE: [(1, 5.5), (7, 3.5), (14, 2.5), (28, 1.5), (60, 1.0)],
    ProcedureType.MENISCUS: [(1, 4.5), (5, 3.0), (10, 2.0), (21, 1.0), (60, 0.5)],
}
PAIN_PUBLISHED = {ProcedureType.TKA, ProcedureType.THA}
REBOUND_WINDOW: dict[ProcedureType, tuple[int, int]] = {
    ProcedureType.TKA: (8, 12),
    ProcedureType.THA: (8, 12),
}
PAIN_BASE_BAND = 2.0  # acute-pain MCID is ~1.5-2.0 NRS points
PAIN_REBOUND_WIDENING = 1.5

PAIN_EVIDENCE_PUBLISHED = (
    "Expected curve from published TKA/THA cohorts (TKA: NRS 5.8 on day 1 → 4.6 on day 8 "
    "→ 3.0 on day 29; THA 3.1 → 2.3; n=103 per procedure). Alert band = the acute-pain "
    "MCID of 1.5–2.0 NRS, widened inside the day 8–12 rebound window (provisional). "
    "Persistence required: two days above band, never one. Days 3, 7, 14 and 30 are the "
    "readings associated with chronic post-surgical pain after TKA."
)
PAIN_EVIDENCE_CONSENSUS = (
    "Expected curve is a consensus shape (provisional) — no published day-by-day NRS "
    "anchors exist for this procedure. Alert band = the acute-pain MCID of 1.5–2.0 NRS; "
    "two consecutive days above band are required, and a rise of 2+ points over four "
    "days against a falling curve is treated as a trajectory reversal (Ortho Metrics "
    "Library M18)."
)


def pain_trajectory(
    procedure: ProcedureType,
    postop_day: int,
    pain: dict[int, float],
    surgery_date: date,
) -> OrthoMeasure:
    procedure = ProcedureType(procedure)  # the column is a plain string
    anchors = PAIN_ANCHORS[procedure]
    published = procedure in PAIN_PUBLISHED
    label = "Pain trajectory"
    common = dict(
        key="pain_trajectory",
        name=label,
        family="Pain control",
        source="patient_reported",
        source_label="Patient-reported · AM/PM log",
        unit="/10",
        evidence=PAIN_EVIDENCE_PUBLISHED if published else PAIN_EVIDENCE_CONSENSUS,
        coverage_text=_coverage(pain, postop_day),
        guarded=False,
        reference=round(_interp(anchors, postop_day), 1),
        series_unit="/10",
        series=_series(pain, surgery_date, postop_day),
    )
    recent = [d for d in pain if d > postop_day - RECENT_DAYS]
    if not pain or not recent:
        return OrthoMeasure(
            status=MetricStatus.NODATA,
            status_text="No recent pain log",
            value=None,
            delta=None,
            finding=(
                "No pain entry in the last three days."
                if pain
                else "No pain entries yet — the AM/PM log starts the curve."
            ),
            next_step=None,
            **common,
        )

    latest_day = max(pain)
    latest = pain[latest_day]
    obs = _median2(pain, latest_day) or latest
    expected = _interp(anchors, latest_day)
    lo, hi = REBOUND_WINDOW.get(procedure, (-1, -1))
    widened = lo <= latest_day <= hi
    band = PAIN_BASE_BAND + (PAIN_REBOUND_WIDENING if widened else 0.0)
    above = obs - expected

    prev_obs = _median2(pain, latest_day - 1)
    prev_above = (
        prev_obs - _interp(anchors, latest_day - 1) if prev_obs is not None else None
    )
    above_two_days = above >= band and prev_above is not None and prev_above >= band

    reescalating = False
    if latest_day >= 14:
        window7 = [pain[d] for d in range(latest_day - 7, latest_day) if d in pain]
        reescalating = bool(window7) and obs - min(window7) >= 2.0

    earlier = _median2(pain, latest_day - 4)
    rise = obs - earlier if earlier is not None else None
    curve_falling = _interp(anchors, latest_day) <= _interp(anchors, latest_day - 4)
    rising_flag = rise is not None and rise >= 2.0 and curve_falling
    rising_watch = rise is not None and rise >= 1.0

    proc = procedure.value
    if above_two_days:
        status, text = MetricStatus.FLAG, "Above expected 2 days"
        finding = (
            f"Pain {latest:.1f}/10 — {above:.1f} points above the expected {expected:.1f} "
            f"for day {latest_day}, two days running (band {band:.1f} NRS"
            f"{', widened for the day 8–12 rebound' if widened else ''})."
        )
        next_step = "Ask what changed — sleep, activity, medication timing — and review analgesia."
    elif reescalating:
        status, text = MetricStatus.FLAG, "Re-escalating"
        finding = (
            f"Pain {latest:.1f}/10 has climbed 2+ points above its best level of the last "
            f"week, after day 14 — the re-escalation channel the review reserves for the "
            "wound and mechanical pathways."
        )
        next_step = "Review alongside the wound check and activity; ask about a new event."
    elif rising_flag:
        status, text = MetricStatus.FLAG, "Rising 4 days"
        finding = (
            f"Pain {latest:.1f}/10, up {rise:.1f} points over four days while the expected "
            f"{proc} curve is falling (≈{expected:.1f} on day {latest_day})."
        )
        next_step = "Ask what changed — sleep, activity, medication timing — and review analgesia."
    elif above >= 1.0 or rising_watch:
        status = MetricStatus.WATCH
        text = "Above expected" if above >= 1.0 else "Rising"
        finding = (
            f"Pain {latest:.1f}/10 against an expected ≈{expected:.1f} for day {latest_day}"
            + (f", up {rise:.1f} over four days" if rising_watch else "")
            + ". Not yet persistent — a single day above band is common."
        )
        next_step = "Re-check tomorrow's log before acting; single-day noise is common."
    else:
        status = MetricStatus.OK
        settling = rise is not None and rise <= -0.5
        text = "Settling" if settling else "On curve"
        trend = (
            f"down {abs(rise):.1f} over four days"
            if rise is not None and rise < 0
            else "holding steady"
        )
        finding = (
            f"Pain {latest:.1f}/10 against an expected ≈{expected:.1f} for day {latest_day}; "
            f"{trend}."
        )
        next_step = None

    return OrthoMeasure(
        status=status,
        status_text=text,
        value=f"{latest:.1f}",
        delta=f"{_signed(above)} vs expected {expected:.1f}",
        finding=finding,
        next_step=next_step,
        **common,
    )


# ---------------------------------------------------------------------------
# 2. Load–pain sensitivity (Ortho Metrics Library M2)
# ---------------------------------------------------------------------------

LOAD_EVIDENCE = (
    "Ortho Metrics Library M2 (dose–response, template T-D): next-day pain regressed on "
    "same-day load, computed on day-to-day changes so the recovery trend cannot pass for "
    "tolerance. A flattening slope means tissue tolerance is improving and the step band "
    "can advance; a load spike above ~1.3× the prior week followed by next-day pain is the "
    "M1 overreaching pattern. A progression-decision metric, not a diagnosis."
)
SPIKE_MULT = 1.35
SPIKE_PAIN_RISE = 1.2


def _slope(pairs: list[tuple[float, float]]) -> float | None:
    if len(pairs) < 6:
        return None
    xs = np.array([p[0] for p in pairs])
    ys = np.array([p[1] for p in pairs])
    var = float(np.var(xs))
    if var == 0:
        return None
    return float(np.cov(xs, ys, bias=True)[0, 1] / var)


def load_pain_sensitivity(
    postop_day: int,
    steps: dict[int, float],
    pain: dict[int, float],
    surgery_date: date,
) -> OrthoMeasure:
    common = dict(
        key="load_pain_sensitivity",
        name="Load–pain sensitivity",
        family="Load & tissue tolerance",
        source="derived",
        source_label="Derived · steps × next-day pain log",
        unit="NRS / 1k steps",
        evidence=LOAD_EVIDENCE,
        guarded=False,
        reference=None,
        series_unit="steps",
        series=_series(steps, surgery_date, postop_day, digits=0),
    )
    post_steps = {d: v for d, v in steps.items() if d >= 0}
    pairs: list[tuple[int, float, float]] = []
    for d in sorted(post_steps):
        if d - 1 in post_steps and d in pain and d + 1 in pain:
            dx = (post_steps[d] - post_steps[d - 1]) / 1000.0
            dy = pain[d + 1] - pain[d]
            pairs.append((d, dx, dy))
    recent = [(dx, dy) for d, dx, dy in pairs if d > postop_day - 14]
    prior = [(dx, dy) for d, dx, dy in pairs if postop_day - 28 < d <= postop_day - 14]
    slope = _slope(recent)
    prior_slope = _slope(prior)
    # A slope only means something when pain changes actually follow load
    # changes; on a flat step count the fitted line is noise with a sign.
    corr = _pearson([p[0] for p in recent], [p[1] for p in recent])
    established = corr is not None and corr >= 0.3
    coverage = f"{len(recent)} paired days in the last 14"

    if not post_steps:
        return OrthoMeasure(
            status=MetricStatus.NODATA, status_text="Needs step data", value=None, delta=None,
            finding="This patient's device does not report daily steps, so load cannot be paired with pain.",
            next_step=None, coverage_text=coverage, **common,
        )
    if slope is None:
        return OrthoMeasure(
            status=MetricStatus.NODATA, status_text="Needs more paired days", value=None,
            delta=None,
            finding=(
                f"Only {len(recent)} days with both a step count and a next-day pain log — "
                "six are needed before a tolerance slope means anything."
            ),
            next_step=None, coverage_text=coverage, **common,
        )

    # the M1 overreaching pattern: one big day, next-day pain
    event: tuple[int, float, float] | None = None
    for d in sorted(post_steps):
        if d <= postop_day - 7 or d < 4:
            continue
        window = [post_steps[k] for k in range(d - 7, d) if k in post_steps]
        if len(window) < 3 or d not in pain or d + 1 not in pain:
            continue
        med = float(np.median(window))
        rise = pain[d + 1] - pain[d]
        if med > 0 and post_steps[d] > SPIKE_MULT * med and rise >= SPIKE_PAIN_RISE:
            event = (d, (post_steps[d] / med - 1.0) * 100.0, rise)

    # pain rising while load falls — the opposite of a load response
    latest_pain = _median2(pain, max(pain)) if pain else None
    earlier_pain = _median2(pain, max(pain) - 4) if pain else None
    recent_steps = [post_steps[d] for d in range(postop_day - 2, postop_day + 1) if d in post_steps]
    earlier_steps = [post_steps[d] for d in range(postop_day - 7, postop_day - 3) if d in post_steps]
    not_load_driven = (
        latest_pain is not None
        and earlier_pain is not None
        and latest_pain - earlier_pain >= 1.5
        and recent_steps
        and earlier_steps
        and float(np.mean(recent_steps)) < 0.85 * float(np.mean(earlier_steps))
    )

    trend = ""
    if prior_slope is not None:
        trend = (
            f", down from {prior_slope:.2f} in the prior two weeks"
            if slope <= prior_slope - 0.3
            else f", up from {prior_slope:.2f} in the prior two weeks"
            if slope >= prior_slope + 0.3
            else f", unchanged from the prior two weeks ({prior_slope:.2f})"
        )
    delta = f"was {prior_slope:.2f} in the prior two weeks" if prior_slope is not None else None

    steep = established and slope >= 0.5
    if event and established and slope >= 1.0:
        status, text = MetricStatus.FLAG, "Irritability rising"
        next_step = "Pull the step band back and review with PT before advancing."
    elif event:
        status, text = MetricStatus.WATCH, "Overdid it once"
        next_step = "Hold the step band this week; advance when the slope flattens."
    elif not_load_driven:
        status, text = MetricStatus.WATCH, "Not load-driven"
        next_step = "Review alongside the wound check and vitals — the pain is not tracking activity."
    elif steep:
        status, text = MetricStatus.WATCH, "Tolerance still low"
        next_step = "Hold the step band this week; advance when the slope flattens."
    else:
        status = MetricStatus.OK
        text = (
            "Tolerance improving"
            if established and prior_slope is not None and slope <= prior_slope - 0.3
            else "No load response"
            if not established
            else "Tolerance stable"
        )
        next_step = None

    if event:
        d, pct, rise = event
        finding = (
            f"Steps rose {pct:.0f}% above the prior week on day {d} and pain rose "
            f"{_signed(rise)} the next day. Tolerance slope {slope:.2f} NRS per 1,000 extra "
            f"steps over the last 14 days{trend}."
        )
    elif not_load_driven:
        finding = (
            "Pain is rising while activity falls — not a load response, so advancing or "
            f"holding the step band will not change it. Tolerance slope {slope:.2f} NRS per "
            "1,000 steps."
        )
    elif not established or slope <= 0.1:
        finding = (
            "Pain changes are not following step changes over the last 14 days"
            + (f" (r = {corr:.2f})" if corr is not None else "")
            + f" — no measurable next-day cost from extra load; fitted slope "
            f"{slope:.2f} NRS per 1,000 steps{trend}."
        )
    else:
        finding = (
            f"Each extra 1,000 steps costs about {slope:.2f} NRS the next day over the last "
            f"14 days{trend}."
        )

    return OrthoMeasure(
        status=status, status_text=text, value=f"{slope:.2f}", delta=delta,
        finding=finding, next_step=next_step, coverage_text=coverage, **common,
    )


# ---------------------------------------------------------------------------
# 3. Range of motion vs milestone
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class RomProtocol:
    motion: str
    label: str
    gates: tuple[tuple[int, float], ...]  # (due by post-op day, degrees)
    evidence: str
    extension: tuple[int, float] | None = None  # (due by day, max deficit °)
    trend_only: bool = False


ROM_PROTOCOLS: dict[ProcedureType, RomProtocol | None] = {
    ProcedureType.TKA: RomProtocol(
        "flexion", "Knee flexion", ((7, 90.0), (21, 100.0), (42, 110.0)),
        "TKA gates: ≥90° flexion by end of week 1; ≥100° and full 0° extension by weeks 2–3; "
        "110–120° by weeks 4–6; <90° at week 6 → MUA discussion. The week-6 projection "
        "(Theil–Sen over readings since day 10) fires on days 21–35 so the PT window is used "
        "with runway, not after the decision is forced. Best MUA gains come within 12 weeks.",
        extension=(21, 5.0),
    ),
    ProcedureType.ACL: RomProtocol(
        "flexion", "Knee flexion", ((14, 90.0), (42, 120.0)),
        "ACL: extension first — full passive 0° by weeks 1–2, flexion ~90° by weeks 1–2 and "
        "120–130° by weeks 4–6. An extension deficit >5–10° persisting past weeks 4–6 is the "
        "arthrofibrosis / cyclops-lesion review threshold.",
        extension=(28, 5.0),
    ),
    ProcedureType.THA: RomProtocol(
        "flexion", "Hip flexion", ((14, 90.0),),
        "Consensus milestone (provisional): hip flexion ≥90° by week 2 within precautions. No "
        "published day-by-day hip ROM curve exists, so this is a milestone check, not a "
        "curve fit.",
    ),
    ProcedureType.ROTATOR_CUFF: RomProtocol(
        "passive_elevation", "Passive forward elevation", ((21, 90.0), (56, 120.0)),
        "MGH 2020 phase protocol (consensus, site-overridable): 90° passive elevation to exit "
        "phase I (weeks 0–3), 120° by phase III (weeks 7–8). Rendered as the surgeon's "
        "protocol, never as a patient instruction.",
    ),
    ProcedureType.ANKLE: RomProtocol(
        "dorsiflexion", "Ankle dorsiflexion", (),
        "No published week-by-week ROM curve for ankle ORIF — the review's instruction is to "
        "trend without alarms rather than fabricate a milestone.",
        trend_only=True,
    ),
    ProcedureType.MENISCUS: RomProtocol(
        "flexion", "Knee flexion", (),
        "No published week-by-week ROM curve for meniscus repair — trended, not judged.",
        trend_only=True,
    ),
    ProcedureType.LUMBAR: None,
}


def range_of_motion(
    procedure: ProcedureType,
    postop_day: int,
    rom: list[RomPoint],
    surgery_date: date,
) -> OrthoMeasure:
    procedure = ProcedureType(procedure)  # the column is a plain string
    protocol = ROM_PROTOCOLS.get(procedure)
    base = dict(
        key="range_of_motion",
        name="Range of motion",
        family="Functional milestone",
        unit="°",
        guarded=False,
        series_unit="°",
    )
    if protocol is None:
        return OrthoMeasure(
            source="clinician_entered", source_label="Not tracked for this procedure",
            status=MetricStatus.NODATA, status_text="Not tracked by design", value=None,
            delta=None,
            finding=(
                "Spine range of motion is intentionally not an early rehab target after "
                "lumbar decompression; walking tolerance is the functional measure here "
                "(see Supporting signals)."
            ),
            next_step=None,
            evidence=(
                "Clinical content review §4.3.6: do not track spine ROM early — track walking "
                "volume (~3,500 steps/day in weeks 1–2, building to 20–30 min continuous "
                "walks) and the pain curve instead."
            ),
            coverage_text="—", reference=None, series=[], **base,
        )

    pts = sorted((p for p in rom if p.motion == protocol.motion), key=lambda p: p.day)
    ext = sorted((p for p in rom if p.motion == "extension_deficit"), key=lambda p: p.day)
    series = _series({p.day: p.degrees for p in pts}, surgery_date, postop_day, digits=0)
    due = [g for g in protocol.gates if g[0] <= postop_day]
    upcoming = [g for g in protocol.gates if g[0] > postop_day]
    reference = (
        float(due[-1][1]) if due else float(upcoming[0][1]) if upcoming else None
    )
    common = dict(
        evidence=protocol.evidence,
        coverage_text=f"{len(pts)} readings · last on day {pts[-1].day}" if pts else "no readings",
        reference=reference,
        series=series,
        **base,
    )
    if not pts:
        return OrthoMeasure(
            source="clinician_entered", source_label="Clinic goniometer · phone self-measure",
            status=MetricStatus.NODATA, status_text="No ROM reading yet", value=None,
            delta=None, finding=f"No {protocol.label.lower()} reading has been entered yet.",
            next_step=None, **common,
        )

    latest = pts[-1]
    source = "clinician_entered" if latest.measured_by == "clinic" else "patient_reported"
    source_label = (
        "Clinic goniometer" if latest.measured_by == "clinic" else "Phone inclinometer · self-measure"
    )
    delta = f"{latest.measured_by} · day {latest.day}"
    plateau = (
        len(pts) >= 3
        and max(p.degrees for p in pts[-3:]) - min(p.degrees for p in pts[-3:]) <= 3.0
        and pts[-1].day - pts[-3].day >= 5
    )
    label = protocol.label

    if protocol.trend_only:
        first = pts[0]
        finding = (
            f"{label} {latest.degrees:.0f}° on day {latest.day}, from {first.degrees:.0f}° on "
            f"day {first.day}. No ROM alarm is set for this procedure — there is no published "
            "milestone to judge it against — so it is trended, not judged."
        )
        if plateau:
            finding += f" Readings have been flat since day {pts[-3].day}."
        return OrthoMeasure(
            source=source, source_label=source_label, status=MetricStatus.OK,
            status_text="Trend only", value=f"{latest.degrees:.0f}", delta=delta,
            finding=finding,
            next_step="Mention the flat readings to PT." if plateau else None,
            **common,
        )

    status = MetricStatus.OK
    text = "On milestone"
    next_step: str | None = None
    finding = ""

    projection: tuple[float, float] | None = None
    if procedure is ProcedureType.TKA and 21 <= postop_day <= 35:
        recent = [(p.day, p.degrees) for p in pts if p.day >= 10]
        fit = _theil_sen(recent)
        if fit is not None:
            slope, intercept = fit
            projected = intercept + slope * 42
            if projected < 90.0:
                projection = (projected, slope)

    if procedure is ProcedureType.TKA and postop_day >= 42 and latest.degrees < 90.0:
        status, text = MetricStatus.FLAG, "Below 90° at week 6"
        finding = (
            f"{label} {latest.degrees:.0f}° on day {latest.day}, under 90° past week 6 — the "
            "range where MUA is typically discussed; gains are best within 12 weeks."
        )
        next_step = "Discuss MUA timing; confirm with a clinic goniometer reading."
    elif projection is not None:
        projected, slope = projection
        status, text = MetricStatus.FLAG, "Tracking to miss 90° at week 6"
        finding = (
            f"{label} is tracking toward roughly {projected:.0f}° at week 6 on the current "
            f"slope ({slope:.1f}°/day). MUA is typically discussed below 90°, and manipulation "
            "within 12 weeks yields the best flexion gains — intensified PT now is the "
            "intervention window."
        )
        next_step = "Intensify PT now; confirm with a clinic reading before escalating."
    elif due and latest.degrees < due[-1][1]:
        gate_day, gate_val = due[-1]
        overdue = postop_day - gate_day >= 7
        status = MetricStatus.FLAG if overdue else MetricStatus.WATCH
        text = f"Below week-{_week(gate_day)} gate"
        finding = (
            f"{label} {latest.degrees:.0f}° on day {latest.day}, under the ≥{gate_val:.0f}° "
            f"milestone due by day {gate_day}"
            + (f" and flat since day {pts[-3].day}" if plateau else "")
            + "."
        )
        next_step = "Review ROM progression with PT; a clinic reading confirms the phone value."
    elif plateau and upcoming and latest.degrees < upcoming[0][1]:
        gate_day, gate_val = upcoming[0]
        status, text = MetricStatus.WATCH, "Plateaued"
        finding = (
            f"{label} flat at {latest.degrees:.0f}° since day {pts[-3].day}, short of the "
            f"≥{gate_val:.0f}° milestone due by day {gate_day}."
        )
        next_step = "Ask PT whether the plan needs a change before the milestone is missed."
    elif upcoming:
        gate_day, gate_val = upcoming[0]
        ahead = latest.degrees >= gate_val
        text = "Ahead of milestone" if ahead else "On milestone"
        finding = (
            f"{label} {latest.degrees:.0f}° on day {latest.day}; "
            + (
                f"already past the ≥{gate_val:.0f}° milestone due by day {gate_day}."
                if ahead
                else f"on track for the ≥{gate_val:.0f}° milestone due by day {gate_day}."
            )
        )
    else:
        text = "Past every gate"
        finding = (
            f"{label} {latest.degrees:.0f}° on day {latest.day}; past every milestone in the "
            "protocol."
        )

    if protocol.extension and ext:
        e_latest = ext[-1]
        ext_day, ext_max = protocol.extension
        if postop_day >= ext_day and e_latest.degrees > ext_max:
            severe = postop_day >= ext_day + 14
            worse = MetricStatus.FLAG if severe else MetricStatus.WATCH
            if _rank(worse) > _rank(status):
                status, text = worse, "Extension deficit"
            finding += (
                f" Extension deficit {e_latest.degrees:.0f}° persists past day {ext_day} — the "
                "arthrofibrosis/cyclops review threshold is >5–10°."
            )
            next_step = next_step or "Prioritise extension work with PT; review if it persists."
        else:
            finding += f" Extension deficit {e_latest.degrees:.0f}°."

    return OrthoMeasure(
        source=source, source_label=source_label, status=status, status_text=text,
        value=f"{latest.degrees:.0f}", delta=delta, finding=finding, next_step=next_step,
        **common,
    )


def _rank(status: MetricStatus) -> int:
    return {MetricStatus.NODATA: 0, MetricStatus.OK: 1, MetricStatus.WATCH: 2, MetricStatus.FLAG: 3}[status]


# ---------------------------------------------------------------------------
# 4. Incision drainage — the week-conditional ladder
# ---------------------------------------------------------------------------

WOUND_LABELS = ["None", "Minimal", "Mild", "Moderate", "Heavy"]
WOUND_EVIDENCE = (
    "Week-conditional ladder from Wouthuyzen-Bakker et al. 2023 (n=1,019, PMC10015257): "
    "any drainage in week 2 was present in 12% of uncomplicated recoveries vs 88% of those "
    "that developed a joint infection; moderate–heavy drainage in week 3 carried a PPV of "
    "83%; no drainage at all, an NPV above 98%. Week-1 drainage alone never alerts (present "
    "in about half of uncomplicated patients). Persistent drainage per ICM: >2×2 cm on the "
    "dressing beyond 72 h. Rendered as a drainage pattern for review, never as a verdict."
)


def wound_drainage(postop_day: int, grades: dict[int, int], surgery_date: date) -> OrthoMeasure:
    common = dict(
        key="wound_drainage",
        name="Incision drainage",
        family="Complication surveillance",
        source="patient_reported",
        source_label="Patient-reported · daily wound check",
        unit="",
        evidence=WOUND_EVIDENCE,
        coverage_text=_coverage({d: float(g) for d, g in grades.items()}, postop_day, noun="checks"),
        guarded=True,
        reference=None,
        series_unit="grade",
        series=_series({d: float(g) for d, g in grades.items()}, surgery_date, postop_day, digits=0),
    )
    recent = [d for d in grades if d > postop_day - RECENT_DAYS]
    if not grades or not recent:
        return OrthoMeasure(
            status=MetricStatus.NODATA, status_text="No recent wound check", value=None,
            delta=None,
            finding=(
                "No wound check logged in the last three days."
                if grades
                else "No wound check logged yet."
            ),
            next_step="Ask for today's dressing check at the next contact.", **common,
        )

    latest_day = max(grades)
    latest = grades[latest_day]
    drainage_days = sorted(d for d, g in grades.items() if g >= 1)
    week2 = [d for d in drainage_days if 8 <= d <= 14]
    week3 = [d for d in drainage_days if 15 <= d <= 21]
    modheavy_w2 = any(grades[d] >= 3 for d in week2)
    modheavy_w3 = any(grades[d] >= 3 for d in week3)
    week1_dry_after_72h = not any(grades.get(d, 0) >= 1 for d in range(4, 8))
    new_onset_w2 = week1_dry_after_72h and bool(week2)
    cum_w1_3 = len([d for d in drainage_days if 1 <= d <= 21])

    def run_length(min_grade: int) -> int:
        n, d = 0, latest_day
        while d in grades and grades[d] >= min_grade:
            n += 1
            d -= 1
        return n

    run_any = run_length(1)
    run_dressing = run_length(2)
    n_days = len(drainage_days)
    week = _week(postop_day)
    delta = f"{n_days} drainage day{'s' if n_days != 1 else ''} · week {week}"
    value = WOUND_LABELS[min(max(latest, 0), 4)]

    if modheavy_w3:
        status, text = MetricStatus.FLAG, "Call today"
        finding = (
            "Moderate-to-heavy drainage reported in post-op week 3. In the reference cohort, "
            "83% of patients with this pattern went on to a confirmed joint infection — a "
            "pattern that warrants a call today."
        )
        next_step = "Call the patient today and bring the wound review forward; ask for a dressing photo."
    elif modheavy_w2:
        status, text = MetricStatus.FLAG, "Call today"
        finding = (
            f"Moderate-to-heavy drainage reported in post-op week 2 (day {latest_day}, "
            f"{WOUND_LABELS[latest].lower()} on the latest check). Week-2 drainage of any "
            "amount was present in 12% of uncomplicated recoveries vs 88% of those that "
            "developed a joint infection — a pattern that warrants a call today."
        )
        next_step = "Call the patient today and bring the wound review forward; ask for a dressing photo."
    elif new_onset_w2:
        status, text = MetricStatus.FLAG, "Call today"
        finding = (
            f"Drainage newly appeared in week 2 (day {week2[0]}) after a dry first week — the "
            "new-onset pattern the reference cohort singles out."
        )
        next_step = "Call the patient today and bring the wound review forward; ask for a dressing photo."
    elif n_days > 10:
        status, text = MetricStatus.FLAG, "Call today"
        finding = f"Drainage reported on {n_days} days (specificity 97% above 10 days)."
        next_step = "Call the patient today and bring the wound review forward."
    elif week2:
        status, text = MetricStatus.FLAG, "Contact today"
        finding = (
            f"Drainage reported in post-op week 2 (day {week2[-1]}) — present in 12% of "
            "uncomplicated recoveries vs 88% of those that developed a joint infection in the "
            "reference cohort."
        )
        next_step = "Contact the patient today about the drainage and confirm dressing changes."
    elif run_any > 5:
        status, text = MetricStatus.FLAG, "Contact today"
        finding = f"Persistent drainage on {run_any} consecutive days — past the 5-day rung of the ICM ladder."
        next_step = "Contact the patient today about the drainage and confirm dressing changes."
    elif cum_w1_3 > 5:
        status, text = MetricStatus.FLAG, "Contact today"
        finding = (
            f"{cum_w1_3} drainage days across weeks 1–3 (sensitivity 63%, specificity 87% "
            "above 5 days)."
        )
        next_step = "Contact the patient today about the drainage and confirm dressing changes."
    elif run_dressing >= 3 and latest_day > 3:
        status, text = MetricStatus.WATCH, "Monitor past 72 h"
        finding = (
            f"Dressing >2×2 cm on {run_dressing} consecutive days beyond 72 h — persistent "
            "wound drainage by the ICM definition, which mandates monitoring past 72 h."
        )
        next_step = "Keep the daily wound check going; re-check the dressing size tomorrow."
    elif latest >= 1 and postop_day <= 7:
        status, text = MetricStatus.OK, "Expected in week 1"
        finding = (
            f"{WOUND_LABELS[latest]} drainage on day {latest_day}. Light drainage in the first "
            "week is present in about half of uncomplicated recoveries and is not alerted on "
            "alone."
        )
        next_step = None
    elif postop_day >= 14 and not any(grades.get(d, 0) >= 1 for d in range(4, postop_day + 1)):
        status, text = MetricStatus.OK, "Dry through day 14"
        finding = (
            f"No drainage reported on any check since 72 h, through day {postop_day}. In the "
            "reference cohort 1 of 467 patients with no drainage developed a joint infection "
            "(NPV >98%)."
        )
        next_step = None
    else:
        status, text = MetricStatus.OK, "Dry"
        last = drainage_days[-1] if drainage_days else None
        finding = (
            f"No drainage on the latest check (day {latest_day})"
            + (f"; none since day {last}." if last is not None else "; none reported at all.")
        )
        next_step = None

    return OrthoMeasure(
        status=status, status_text=text, value=value, delta=delta, finding=finding,
        next_step=next_step, **common,
    )


# ---------------------------------------------------------------------------
# 5. Nocturnal disruption pain-proxy (Ortho Metrics Library M9)
# ---------------------------------------------------------------------------

NOCTURNAL_EVIDENCE = (
    "Ortho Metrics Library M9 (nocturnal disruption pain-proxy). Built on fragmentation, "
    "never duration: Gibian et al. 2023 (J Arthroplasty, n=110) found wearable total sleep "
    "time unchanged at 30/60/90 days and uncorrelated with pain at every timepoint, while "
    "awakenings, WASO and efficiency carry the signal. Night pain is chronically "
    "under-reported by day; strongest for shoulder, where it is the classic complaint."
)


def nocturnal_disruption(
    postop_day: int,
    awakenings: dict[int, float],
    evening_pain: dict[int, float],
    surgery_date: date,
) -> OrthoMeasure:
    pre = [v for d, v in awakenings.items() if d < 0]
    baseline = float(np.mean(pre)) if len(pre) >= 5 else None
    common = dict(
        key="nocturnal_disruption",
        name="Nocturnal disruption",
        family="Recovery quality",
        source="derived",
        source_label="Derived · wearable sleep record × evening pain",
        unit="/night",
        evidence=NOCTURNAL_EVIDENCE,
        coverage_text=_coverage(awakenings, postop_day, noun="nights"),
        guarded=False,
        reference=round(baseline, 1) if baseline is not None else None,
        series_unit="awakenings",
        series=_series(awakenings, surgery_date, postop_day, digits=0),
    )
    recent_nights = sorted(d for d in awakenings if d > postop_day - 5 and d >= 0)[-3:]
    if len(recent_nights) < 2 or recent_nights[-1] <= postop_day - RECENT_DAYS:
        return OrthoMeasure(
            status=MetricStatus.NODATA, status_text="No recent overnight data", value=None,
            delta=None,
            finding="Fewer than two nights of overnight wear in the last five days — the watch has to be on the wrist overnight for this measure.",
            next_step="Ask the patient to wear the watch overnight.", **common,
        )

    recent = float(np.mean([awakenings[d] for d in recent_nights]))
    prior_nights = [d for d in awakenings if postop_day - 8 < d <= postop_day - 3 and d >= 0]
    prior = float(np.mean([awakenings[d] for d in prior_nights])) if len(prior_nights) >= 2 else None
    elevated = baseline is not None and recent >= baseline + 2.0
    rising = prior is not None and recent >= prior + 1.0
    pairs = [(awakenings[d], evening_pain[d]) for d in awakenings if d >= 0 and d in evening_pain]
    corr = _pearson([p[0] for p in pairs], [p[1] for p in pairs])
    tracks = corr is not None and corr >= 0.5
    ref = f"against {baseline:.1f} before surgery" if baseline is not None else "with no pre-op nights to compare"

    if postop_day <= 3:
        status, text = MetricStatus.OK, "Early post-op"
        finding = (
            f"{recent:.1f} awakenings a night {ref}. The first nights after surgery are "
            "expected to be fragmented (deep-sleep fraction is reduced only in week 1); "
            "the proxy starts judging from night 4."
        )
        next_step = None
    elif elevated and tracks:
        status, text = MetricStatus.FLAG, "Fragmented, tracks evening pain"
        finding = (
            f"{recent:.1f} awakenings a night over the last three nights {ref}, and the "
            f"nightly count moves with the evening pain log (r = {corr:.2f}). Nighttime pain "
            "is chronically under-reported by day — this is the objective signal that pain "
            "is not controlled overnight."
        )
        next_step = "Ask about night pain and analgesia timing; consider a bedtime dose or positioning advice."
    elif elevated:
        status, text = MetricStatus.WATCH, "Fragmented nights"
        finding = (
            f"{recent:.1f} awakenings a night over the last three nights {ref}. Not yet "
            "tracking the evening pain log, so position and environment are as likely as pain."
        )
        next_step = "Ask about sleep position and evening pain at the next check-in."
    elif rising and tracks:
        status, text = MetricStatus.WATCH, "Rising with evening pain"
        finding = (
            f"{recent:.1f} awakenings a night, up from {prior:.1f} earlier in the week, and "
            f"moving with the evening pain log (r = {corr:.2f})."
        )
        next_step = "Ask about sleep position and evening pain at the next check-in."
    else:
        status = MetricStatus.OK
        settling = prior is not None and recent < prior - 0.5
        text = "Settling" if settling else "Consolidated"
        finding = (
            f"{recent:.1f} awakenings a night over the last three nights"
            + (f", down from {prior:.1f} earlier in the week" if settling else "")
            + (
                f" — consolidating toward the pre-op {baseline:.1f}."
                if baseline is not None
                else "."
            )
        )
        next_step = None

    return OrthoMeasure(
        status=status, status_text=text, value=f"{recent:.1f}",
        delta=f"vs {baseline:.1f} pre-op" if baseline is not None else None,
        finding=finding, next_step=next_step, **common,
    )


# ---------------------------------------------------------------------------
# builder
# ---------------------------------------------------------------------------

_METRICS = [M.PAIN_NRS, M.RANGE_OF_MOTION, M.WOUND_DRAINAGE, M.SLEEP_AWAKENINGS, M.STEPS]


def _load(db: Session, patient: Patient) -> dict[str, Any]:
    rows = db.execute(
        select(
            Observation.metric_type,
            Observation.local_date,
            Observation.value_num,
            Observation.value_json,
        )
        .where(
            Observation.patient_id == patient.id,
            Observation.metric_type.in_([str(m) for m in _METRICS]),
            Observation.value_num.is_not(None),
            Observation.deleted_at.is_(None),
        )
        .order_by(Observation.local_date)
    ).all()
    pain: dict[int, list[float]] = {}
    evening: dict[int, float] = {}
    steps: dict[int, list[float]] = {}
    wound: dict[int, int] = {}
    awakenings: dict[int, list[float]] = {}
    rom: list[RomPoint] = []
    for metric, local_date, value, value_json in rows:
        day = (local_date - patient.surgery_date).days
        metric = str(metric)
        info = value_json or {}
        if metric == str(M.PAIN_NRS):
            pain.setdefault(day, []).append(float(value))
            if "pm" in info:
                evening[day] = float(info["pm"])
        elif metric == str(M.STEPS):
            steps.setdefault(day, []).append(float(value))
        elif metric == str(M.WOUND_DRAINAGE):
            wound[day] = max(wound.get(day, 0), int(round(float(value))))
        elif metric == str(M.SLEEP_AWAKENINGS):
            awakenings.setdefault(day, []).append(float(value))
        elif metric == str(M.RANGE_OF_MOTION):
            rom.append(
                RomPoint(
                    day=day,
                    motion=str(info.get("motion", "flexion")),
                    degrees=float(value),
                    measured_by=str(info.get("measured_by", "clinic")),
                )
            )
    mean = lambda d: {k: float(np.mean(v)) for k, v in d.items()}  # noqa: E731
    pain_mean = mean(pain)
    return {
        "pain": pain_mean,
        "evening_pain": {d: evening.get(d, v) for d, v in pain_mean.items()},
        "steps": mean(steps),
        "wound": wound,
        "awakenings": mean(awakenings),
        "rom": rom,
    }


def build_ortho_measures(
    db: Session, patient: Patient, today: date | None = None
) -> dict[str, Any]:
    today = today or date.today()
    postop_day = (today - patient.surgery_date).days
    data = _load(db, patient)
    surgery = patient.surgery_date
    measures = [
        pain_trajectory(patient.procedure_type, postop_day, data["pain"], surgery),
        load_pain_sensitivity(postop_day, data["steps"], data["pain"], surgery),
        range_of_motion(patient.procedure_type, postop_day, data["rom"], surgery),
        wound_drainage(postop_day, data["wound"], surgery),
        nocturnal_disruption(postop_day, data["awakenings"], data["evening_pain"], surgery),
    ]
    return {
        "postop_day": postop_day,
        "generated_at": datetime.now().isoformat(),
        "summary": {
            "flagged": sum(m.status is MetricStatus.FLAG for m in measures),
            "watch": sum(m.status is MetricStatus.WATCH for m in measures),
            "nodata": sum(m.status is MetricStatus.NODATA for m in measures),
        },
        "provenance": PROVENANCE,
        "measures": [asdict(m) for m in measures],
    }


def notable_ortho(measures: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """The compact view the narrative prompts and the roster Q&A read: every
    measure's status and finding, no chart points."""
    return [
        {
            "name": m["name"],
            "status": str(m["status"]),
            "status_text": m["status_text"],
            "value": f"{m['value']}{m['unit']}" if m.get("value") is not None else None,
            "finding": m["finding"],
        }
        for m in measures
    ]
