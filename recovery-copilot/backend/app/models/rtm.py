from datetime import date, datetime

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class MonitoringWindow(Base):
    """RTM scaffolding: measurement-day coverage per rolling 30-day window.

    Mirrors CPT 99454-style requirements (>=16 days with device data per 30-day
    period). Architectural capability only — no billing workflow in v1.
    """

    __tablename__ = "monitoring_windows"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    patient_id: Mapped[str] = mapped_column(ForeignKey("patients.id"), index=True)
    window_start: Mapped[date] = mapped_column(Date)
    window_end: Mapped[date] = mapped_column(Date)
    days_with_data: Mapped[int] = mapped_column(Integer)
    qualifies_16_of_30: Mapped[bool] = mapped_column(Boolean)
    computed_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now)
