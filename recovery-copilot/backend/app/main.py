from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api import api_router
from app.config import PROJECT_DIR

app = FastAPI(title="MedPull Recovery Copilot", version="1.0.0")


@app.on_event("startup")
def warm_caches() -> None:
    """Generate any missing assessments + narratives in the background so the
    first page load doesn't wait on a cold LLM."""
    import logging
    import threading

    def _warm() -> None:
        try:
            from app.database import SessionLocal
            from app.engine.pipeline import run_all
            from app.llm.insights import get_daily_briefing, get_patient_insight
            from app.models.enums import InsightKind
            from app.models.patient import Patient
            from sqlalchemy import select

            db = SessionLocal()
            try:
                run_all(db)
                for pid in db.scalars(select(Patient.id)).all():
                    for kind in (
                        InsightKind.WORKLIST_REASON,
                        InsightKind.PATIENT_SUMMARY,
                        InsightKind.SUGGESTED_ACTIONS,
                    ):
                        get_patient_insight(db, kind, pid)
                get_daily_briefing(db)
                logging.getLogger(__name__).info("Insight caches warmed")
            finally:
                db.close()
        except Exception:  # noqa: BLE001 — warming must never take the app down
            logging.getLogger(__name__).exception("Cache warming failed")

    threading.Thread(target=_warm, name="insight-warmer", daemon=True).start()

# Dev convenience: the Vite dev server proxies /api, but allow direct calls too.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)

# Single-process demo mode: serve the built SPA. API routes are registered
# above, so they win; everything else falls back to index.html for client
# routing (deep links like /patients/marcus).
DIST = PROJECT_DIR / "frontend" / "dist"
if DIST.exists():
    app.mount("/assets", StaticFiles(directory=DIST / "assets"), name="assets")

    @app.get("/{full_path:path}", include_in_schema=False)
    def spa(full_path: str) -> FileResponse:
        candidate = DIST / full_path
        if full_path and candidate.is_file():
            return FileResponse(candidate)
        # index.html must always revalidate: it points at content-hashed
        # bundles, and a cached copy would keep serving a stale build.
        return FileResponse(DIST / "index.html", headers={"Cache-Control": "no-cache"})
