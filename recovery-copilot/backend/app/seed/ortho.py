"""Orthopedic measure streams for the demo roster.

Patient-reported pain (NRS, AM/PM) and daily wound checks, clinic- and
phone-measured range of motion, and nightly awakenings from the wearable's
sleep record — the inputs to app.engine.ortho_measures.

Kept apart from the wearable generators on purpose: none of these streams is
read by the risk engine (engine/pipeline.py ANALYZED_METRICS), so the pinned
golden tiers cannot move, and each stream is shaped per patient so the five
measures have something true to find. Deterministic via the same seeded-rng
scheme as the generators.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta

import numpy as np

from app.connectors.base import CanonicalObservation
from app.connectors.capabilities import CAPABILITIES
from app.connectors.mock import UNITS
from app.models.enums import Granularity, ProcedureType, SourceProvider
from app.models.enums import MetricType as M
from app.seed.generators import _rng, dropped_days
from app.seed.patients import PatientSpec
from app.seed.scenarios import Ramp, ScenarioSpec

PRE_OP_NIGHTS = 10

WOUND_LABELS = ["none", "minimal", "mild", "moderate", "heavy"]


@dataclass(frozen=True)
class WoundSegment:
    """Drainage grade reported on every day in [start_day, end_day]."""

    start_day: int
    end_day: int
    grade: int  # 0 none .. 4 heavy


@dataclass(frozen=True)
class OrthoStory:
    # pain: NRS on post-op day 1, the level it settles to, and how fast
    pain_pod1: float = 5.5
    pain_floor: float = 1.0
    pain_tau: float = 9.0
    pain_pm_offset: float = 0.6  # evenings hurt more
    pain_ramps: tuple[Ramp, ...] = ()  # Ramp(M.PAIN_NRS, ..., add=) — persistent
    pain_bumps: tuple[tuple[int, float], ...] = ()  # (day, +NRS) — one day only
    log_miss: float = 0.10  # fraction of post-op days with no pain / wound log
    # wound: segments override the default taper (mild -> dry by day 4)
    wound: tuple[WoundSegment, ...] = ()
    # range of motion: <1 recovers a smaller share of the range, and a plateau
    # freezes progress after that day
    rom_scale: float = 1.0
    rom_plateau_after: int | None = None
    # awakenings per night, on top of the pain-driven baseline
    awakenings_ramps: tuple[Ramp, ...] = ()


@dataclass(frozen=True)
class RomCurve:
    joint: str
    motion: str
    start: float
    target: float
    tau: float
    extension: bool = False  # knees also report an extension deficit


ROM_CURVES: dict[ProcedureType, RomCurve] = {
    ProcedureType.TKA: RomCurve("knee", "flexion", 65.0, 118.0, 14.0, extension=True),
    ProcedureType.ACL: RomCurve("knee", "flexion", 70.0, 128.0, 14.0, extension=True),
    ProcedureType.THA: RomCurve("hip", "flexion", 70.0, 105.0, 12.0),
    ProcedureType.ROTATOR_CUFF: RomCurve("shoulder", "passive_elevation", 60.0, 140.0, 28.0),
    ProcedureType.ANKLE: RomCurve("ankle", "dorsiflexion", 2.0, 16.0, 21.0),
    ProcedureType.MENISCUS: RomCurve("knee", "flexion", 90.0, 130.0, 8.0),
    # lumbar: spine ROM is deliberately not tracked early — no curve
}

# Clinic goniometry on visit days; phone-inclinometer self-measures in between.
CLINIC_DAYS = (1, 7, 14, 21, 28, 42, 56)
PHONE_EVERY = 3

STORIES: dict[str, OrthoStory] = {
    # Rising pain from day 4 against a falling expected curve; the wound goes
    # dry, then drains again from day 6 and reaches moderate in week 2 — the
    # ladder's escalate rung. Nights fragment in step with evening pain.
    "marcus": OrthoStory(
        pain_pod1=6.2, pain_floor=2.5, pain_tau=10.0,
        pain_ramps=(Ramp(M.PAIN_NRS, 4, 8, add=3.2),),
        wound=(
            WoundSegment(0, 2, 2), WoundSegment(3, 5, 0),
            WoundSegment(6, 6, 2), WoundSegment(7, 8, 3),
        ),
        rom_scale=0.9,
        awakenings_ramps=(Ramp(M.SLEEP_AWAKENINGS, 4, 8, add=3.0),),
    ),
    # Shoulder: day pain controlled, night pain not — evenings run hot and the
    # nights fragment from day 4. The nocturnal proxy is the star for shoulder.
    "linda": OrthoStory(
        pain_pod1=6.0, pain_floor=2.5, pain_tau=12.0, pain_pm_offset=1.5,
        awakenings_ramps=(Ramp(M.SLEEP_AWAKENINGS, 3, 6, add=3.0),),
    ),
    "robert": OrthoStory(pain_pod1=5.5, pain_floor=2.0, pain_tau=10.0),
    # Ankle: dorsiflexion stalls at day 10 — trended, not alarmed (by design)
    "sofia": OrthoStory(pain_pod1=5.0, pain_floor=1.5, pain_tau=8.0, rom_plateau_after=10),
    # Hip flexion plateaus short of the week-2 milestone
    "aisha": OrthoStory(
        pain_pod1=3.6, pain_floor=1.5, pain_tau=8.0, rom_scale=0.75, rom_plateau_after=8,
    ),
    # Logs only land on the days the device was worn (see _allowed_days)
    "priya": OrthoStory(pain_pod1=3.5, pain_floor=1.5, pain_tau=8.0, log_miss=0.0),
    # Day 3: a normal first-week wound and a normal first-week pain curve
    "grace": OrthoStory(pain_pod1=3.4, pain_floor=1.5, pain_tau=8.0),
    "david": OrthoStory(pain_pod1=5.5, pain_floor=0.8, pain_tau=8.0),
    "james": OrthoStory(pain_pod1=6.0, pain_floor=1.5, pain_tau=10.0),
    "elena": OrthoStory(pain_pod1=4.5, pain_floor=0.8, pain_tau=6.0),
    # The demo stand-in: settling pain with one next-day bump after the day-10
    # step spike (scenarios.py), a dry wound from day 3, full extension.
    "chris": OrthoStory(pain_pod1=5.8, pain_floor=1.2, pain_tau=7.0, pain_bumps=((11, 2.0),)),
}


def get_story(patient_id: str) -> OrthoStory:
    return STORIES.get(patient_id, OrthoStory())


def _clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def _ramp_add(ramps: tuple[Ramp, ...], metric: M, day: int) -> float:
    return sum(r.add * r.factor(day) for r in ramps if r.metric is metric)


def _pain_curve(story: OrthoStory, day: int) -> float:
    """Recovery-shaped NRS: pain_pod1 on day 1, decaying toward the floor."""
    return story.pain_floor + (story.pain_pod1 - story.pain_floor) * float(
        np.exp(-(day - 1) / story.pain_tau)
    )


def _allowed_days(spec: PatientSpec, scenario: ScenarioSpec) -> set[int] | None:
    """Post-op days a patient-reported entry may land on. A barely-worn device
    (priya) and a barely-used app tell one story: the app is only open on the
    days the watch was on, so the sparse-coverage picture stays sparse."""
    if scenario.dropout_frac < 0.5:
        return None
    return set(range(0, spec.postop_day + 1)) - dropped_days(spec, scenario)


def _reported(
    spec: PatientSpec,
    metric: M,
    day: date,
    value: float,
    value_json: dict | None,
    provider: SourceProvider = SourceProvider.PATIENT_REPORTED,
    start: time = time(7, 30),
    end: time = time(20, 30),
    external_id: str | None = None,
    granularity: Granularity = Granularity.DAILY_SUMMARY,
) -> CanonicalObservation:
    return CanonicalObservation(
        patient_id=spec.id,
        source_provider=provider,
        metric_type=metric,
        unit=UNITS[metric],
        value_num=round(float(value), 2),
        value_json=value_json,
        start_time=datetime.combine(day, start),
        end_time=datetime.combine(day, end),
        granularity=granularity,
        external_id=external_id,
        is_patient_reported=provider is SourceProvider.PATIENT_REPORTED,
        # Demo rows never count toward a billed monitoring day on their own;
        # seed/rtm.py paints qualification explicitly.
        qualifies_for_rtm=False,
    )


def _pain_values(
    spec: PatientSpec, story: OrthoStory, days: list[int]
) -> dict[int, tuple[float, float]]:
    """day -> (am, pm) NRS for every post-op day, before log misses."""
    noise = _rng(spec.id, "ortho:pain").standard_normal(2 * len(days))
    out: dict[int, tuple[float, float]] = {}
    for i, d in enumerate(days):
        base = _pain_curve(story, d) + _ramp_add(story.pain_ramps, M.PAIN_NRS, d)
        base += sum(add for bump_day, add in story.pain_bumps if bump_day == d)
        am = _clamp(base - 0.3 + 0.35 * noise[2 * i], 0.0, 10.0)
        pm = _clamp(am + story.pain_pm_offset + 0.3 * noise[2 * i + 1], 0.0, 10.0)
        out[d] = (am, pm)
    return out


def _wound_grade(story: OrthoStory, day: int) -> int:
    for seg in story.wound:
        if seg.start_day <= day <= seg.end_day:
            return seg.grade
    # default: a little on the dressing for the first days, dry from day 4
    if day <= 1:
        return 2
    if day <= 3:
        return 1
    return 0


def _rom_value(spec: PatientSpec, story: OrthoStory, curve: RomCurve, day: int) -> float:
    eff = min(day, story.rom_plateau_after) if story.rom_plateau_after is not None else day
    recoverable = (curve.target - curve.start) * story.rom_scale
    return curve.start + recoverable * (1.0 - float(np.exp(-eff / curve.tau)))


def _extension_deficit(story: OrthoStory, day: int) -> float:
    eff = min(day, story.rom_plateau_after) if story.rom_plateau_after is not None else day
    # 8 degrees short of full extension at surgery; slower recoveries keep more
    return 8.0 * float(np.exp(-eff / (6.0 / story.rom_scale)))


def generate_ortho_observations(
    spec: PatientSpec, scenario: ScenarioSpec, today: date
) -> list[CanonicalObservation]:
    story = get_story(spec.id)
    surgery = today - timedelta(days=spec.postop_day)
    post_days = list(range(0, spec.postop_day + 1))
    allowed = _allowed_days(spec, scenario)
    miss_rng = _rng(spec.id, "ortho:miss")
    missed = {d for d in post_days if miss_rng.random() < story.log_miss}
    missed.discard(spec.postop_day)  # today's log is the one the demo reads
    for bump_day, _ in story.pain_bumps:  # a bad day is the day a patient does log
        missed.discard(bump_day)
        missed.discard(bump_day - 1)

    def may_log(d: int) -> bool:
        return d not in missed and (allowed is None or d in allowed)

    out: list[CanonicalObservation] = []
    pain = _pain_values(spec, story, post_days)

    # --- patient-reported: pain AM/PM and the daily wound check ---
    for d in post_days:
        if not may_log(d):
            continue
        day_date = surgery + timedelta(days=d)
        am, pm = pain[d]
        out.append(
            _reported(
                spec, M.PAIN_NRS, day_date, (am + pm) / 2.0,
                {"am": round(am, 1), "pm": round(pm, 1), "scale": "NRS 0-10"},
            )
        )
        grade = _wound_grade(story, d)
        out.append(
            _reported(
                spec, M.WOUND_DRAINAGE, day_date, grade,
                {"label": WOUND_LABELS[grade], "dressing_gt_2x2": grade >= 2},
                start=time(8, 0), end=time(8, 5),
            )
        )

    # --- range of motion: clinic goniometry + phone self-measure ---
    curve = ROM_CURVES.get(spec.procedure)
    if curve is not None:
        rom_noise = _rng(spec.id, "ortho:rom").standard_normal(2 * (spec.postop_day + 1))
        for d in post_days:
            clinic = d in CLINIC_DAYS
            phone = d % PHONE_EVERY == 2 and d not in CLINIC_DAYS
            if not (clinic or phone):
                continue
            if allowed is not None and d not in allowed:
                continue
            day_date = surgery + timedelta(days=d)
            measured_by = "clinic" if clinic else "phone"
            provider = (
                SourceProvider.CLINICIAN_ENTERED if clinic else SourceProvider.PATIENT_REPORTED
            )
            at = time(10, 0) if clinic else time(18, 0)
            sd = 1.5 if clinic else 3.0  # phone inclinometer is noisier
            degrees = _clamp(_rom_value(spec, story, curve, d) + sd * rom_noise[2 * d], 0.0, 180.0)
            out.append(
                _reported(
                    spec, M.RANGE_OF_MOTION, day_date, round(degrees),
                    {"joint": curve.joint, "motion": curve.motion, "measured_by": measured_by},
                    provider=provider, start=at, end=at,
                    external_id=f"rom:{curve.motion}:{d}", granularity=Granularity.INSTANT,
                )
            )
            if curve.extension:
                deficit = _clamp(
                    _extension_deficit(story, d) + 0.5 * sd * rom_noise[2 * d + 1], 0.0, 30.0
                )
                out.append(
                    _reported(
                        spec, M.RANGE_OF_MOTION, day_date, round(deficit),
                        {
                            "joint": curve.joint,
                            "motion": "extension_deficit",
                            "measured_by": measured_by,
                        },
                        provider=provider, start=at, end=at,
                        external_id=f"rom:extension_deficit:{d}",
                        granularity=Granularity.INSTANT,
                    )
                )

    # --- wearable: awakenings per night (fragmentation, never duration) ---
    if M.SLEEP_STAGES in CAPABILITIES.get(spec.provider, []):
        nights = list(range(-PRE_OP_NIGHTS, spec.postop_day + 1))
        dropped = dropped_days(spec, scenario)
        r = _rng(spec.id, "ortho:awakenings")
        base = float(r.uniform(0.8, 1.8))
        noise = r.standard_normal(len(nights))
        for i, d in enumerate(nights):
            if d in dropped:
                continue
            v = base
            if d >= 0:
                pm = pain[d][1]
                v += 2.5 * float(np.exp(-d / 4.0)) + 0.5 * max(0.0, pm - 2.5)
                v += _ramp_add(story.awakenings_ramps, M.SLEEP_AWAKENINGS, d)
            count = max(0, int(round(v + 0.5 * noise[i])))
            waso = max(0, int(round(count * 9.0 + 4.0 * noise[i])))
            out.append(
                _reported(
                    spec, M.SLEEP_AWAKENINGS, surgery + timedelta(days=d), count,
                    {"waso_min": waso, "source": "sleep_stages"},
                    provider=spec.provider, start=time(0, 0), end=time(7, 0),
                )
            )
    return out
