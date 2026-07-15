"""RTM monitoring-day coverage (CPT 99454-style: >=16 days of device data per
30-day window). Architectural capability only — no billing workflow in v1."""

from datetime import date, datetime, time, timedelta

from sqlalchemy import distinct, func, select
from sqlalchemy.orm import Session

from app.models.observation import Observation
from app.models.rtm import MonitoringWindow

WINDOW_DAYS = 30
QUALIFY_DAYS = 16


def update_window(db: Session, patient_id: str, today: date | None = None) -> MonitoringWindow:
    today = today or date.today()
    window_start = today - timedelta(days=WINDOW_DAYS - 1)

    days = db.scalar(
        select(func.count(distinct(func.date(Observation.start_time)))).where(
            Observation.patient_id == patient_id,
            Observation.start_time >= datetime.combine(window_start, time.min),
        )
    ) or 0

    row = db.scalar(
        select(MonitoringWindow)
        .where(
            MonitoringWindow.patient_id == patient_id,
            MonitoringWindow.window_start == window_start,
        )
        .limit(1)
    )
    if row is None:
        row = MonitoringWindow(
            patient_id=patient_id, window_start=window_start, window_end=today,
            days_with_data=int(days), qualifies_16_of_30=days >= QUALIFY_DAYS,
        )
        db.add(row)
    else:
        row.window_end = today
        row.days_with_data = int(days)
        row.qualifies_16_of_30 = days >= QUALIFY_DAYS
        row.computed_at = datetime.now()
    db.commit()
    return row


def get_current(db: Session, patient_id: str) -> MonitoringWindow | None:
    return db.scalar(
        select(MonitoringWindow)
        .where(MonitoringWindow.patient_id == patient_id)
        .order_by(MonitoringWindow.window_end.desc(), MonitoringWindow.id.desc())
        .limit(1)
    )
