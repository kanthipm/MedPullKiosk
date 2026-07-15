"""Test fixtures: an isolated seeded SQLite database shared across the suite.

The DATABASE_URL override must land before any app module import, because
app.database binds the engine at import time.
"""

import os
import tempfile

_TMP = tempfile.mkdtemp(prefix="recovery-copilot-tests-")
os.environ["DATABASE_URL"] = f"sqlite:///{_TMP}/test.db"
os.environ["GROQ_API_KEY"] = ""  # force the deterministic fallback everywhere
os.environ["OLLAMA_URL"] = ""  # never let a locally running Ollama into the tests

from datetime import date  # noqa: E402

import pytest  # noqa: E402

import app.models  # noqa: F401, E402
from app.database import Base, SessionLocal, engine  # noqa: E402


@pytest.fixture(scope="session")
def seeded_db():
    """Full seed + engine + insights, once per test session."""
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    db = SessionLocal()
    try:
        from app.seed.seed import seed_core, warm_engine_and_insights

        seed_core(db, date.today())
        warm_engine_and_insights(db)
        yield db
    finally:
        db.close()


@pytest.fixture()
def db(seeded_db):
    return seeded_db


@pytest.fixture(scope="session")
def client(seeded_db):
    from fastapi.testclient import TestClient

    from app.main import app

    return TestClient(app)
