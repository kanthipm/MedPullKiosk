"""The Lambda entrypoint, driven with the event shapes AWS actually sends.

app.lambda_handler does real work at import time — the writable-path guard, the
SSM fetch, the cold-start hydrate — so these tests import it fresh under a
patched environment rather than relying on module state from another test.
"""

import importlib
import json
import sys

import pytest

from app.aws import storage
from app.aws.config import aws_settings
from app.config import settings
from tests.test_aws_storage import FakeS3


def _import_handler(monkeypatch, tmp_path, *, db_dir="/tmp", secret=""):
    """Import app.lambda_handler with AWS wiring pointed at a fake S3."""
    fake = FakeS3()
    db_path = f"{db_dir}/recovery-copilot-handler-test.db"

    monkeypatch.setattr(settings, "database_url", f"sqlite:///{db_path}")
    monkeypatch.setattr(aws_settings, "s3_bucket", "test-bucket")
    monkeypatch.setattr(aws_settings, "origin_verify_secret", secret)
    monkeypatch.setattr(storage, "client", lambda: fake)
    monkeypatch.setattr(storage, "_client", fake)
    monkeypatch.setattr(storage, "_dispose_engine", lambda: None)
    monkeypatch.setattr(storage, "_state", {"etag": None, "checked_at": 0.0, "dirty": False})

    sys.modules.pop("app.lambda_handler", None)
    module = importlib.import_module("app.lambda_handler")
    return module, fake


def _url_event(method="GET", path="/api/health", headers=None, body=None):
    """A Lambda Function URL payload (format 2.0)."""
    return {
        "version": "2.0",
        "routeKey": "$default",
        "rawPath": path,
        "rawQueryString": "",
        "headers": {"host": "abc.lambda-url.us-east-1.on.aws", **(headers or {})},
        "requestContext": {
            "accountId": "anonymous",
            "apiId": "abc",
            "domainName": "abc.lambda-url.us-east-1.on.aws",
            "http": {
                "method": method,
                "path": path,
                "protocol": "HTTP/1.1",
                "sourceIp": "203.0.113.1",
                "userAgent": "pytest",
            },
            "requestId": "req-1",
            "stage": "$default",
            "time": "01/Jan/2026:00:00:00 +0000",
            "timeEpoch": 1767225600000,
        },
        "body": body,
        "isBase64Encoded": False,
    }


def test_refuses_a_database_path_outside_tmp(monkeypatch, tmp_path):
    """/var/task is read-only, and app.config's default points inside it — this
    has to fail loudly at import, not mysteriously at the first query.

    /var/task is used verbatim rather than a tmp_path: pytest's tmp_path lives
    under /tmp, so it would satisfy the guard and prove nothing.
    """
    with pytest.raises(RuntimeError, match="must live under /tmp"):
        _import_handler(monkeypatch, tmp_path, db_dir="/var/task")


def test_serves_an_http_event(monkeypatch, tmp_path):
    handler, fake = _import_handler(monkeypatch, tmp_path)
    # The cold-start hydrate found no object; seed the bucket the way the deploy
    # does, then serve a request off it.
    fake.objects[aws_settings.s3_db_key] = _seeded_db_bytes()
    fake.etags[aws_settings.s3_db_key] = '"seeded"'

    response = handler.handler(_url_event(), None)

    assert response["statusCode"] == 200
    payload = json.loads(response["body"])
    assert payload["status"] == "ok"
    assert payload["db_ok"] is True
    # No key configured in the test env, so the deterministic renderer is active.
    assert payload["llm_provider"] == "fallback"


def test_origin_verification_rejects_a_direct_caller(monkeypatch, tmp_path):
    handler, _ = _import_handler(monkeypatch, tmp_path, secret="s3cret")

    response = handler.handler(_url_event(), None)
    assert response["statusCode"] == 403

    response = handler.handler(_url_event(headers={"x-origin-verify": "guess"}), None)
    assert response["statusCode"] == 403


def test_origin_verification_accepts_cloudfront(monkeypatch, tmp_path):
    handler, fake = _import_handler(monkeypatch, tmp_path, secret="s3cret")
    fake.objects[aws_settings.s3_db_key] = _seeded_db_bytes()
    fake.etags[aws_settings.s3_db_key] = '"seeded"'

    response = handler.handler(_url_event(headers={"X-Origin-Verify": "s3cret"}), None)
    assert response["statusCode"] == 200


def test_seed_action_runs_under_the_lock_and_uploads(monkeypatch, tmp_path):
    handler, fake = _import_handler(monkeypatch, tmp_path)

    called = {}

    def fake_run_seed(reset=False):
        called["reset"] = reset
        assert aws_settings.s3_lock_key in fake.objects, "seed ran without the write lock"
        aws_settings.local_db_path.write_bytes(b"freshly-seeded")
        return {"patients": 10}

    monkeypatch.setattr("app.seed.seed.run_seed", fake_run_seed)

    result = handler.handler({"action": "seed"}, None)

    assert result == {"ok": True, "uploaded": True, "counts": {"patients": 10}}
    assert called["reset"] is True
    assert fake.objects[aws_settings.s3_db_key] == b"freshly-seeded"
    assert aws_settings.s3_lock_key not in fake.objects  # released


def test_unknown_admin_action_is_reported(monkeypatch, tmp_path):
    handler, _ = _import_handler(monkeypatch, tmp_path)
    result = handler.handler({"action": "drop-everything"}, None)
    assert result["ok"] is False
    assert "drop-everything" in result["error"]


def _seeded_db_bytes() -> bytes:
    """Bytes of a real SQLite file with the app's schema, for /api/health."""
    import sqlite3
    import tempfile
    from pathlib import Path

    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "seed.db"
        sqlite3.connect(path).close()
        return path.read_bytes()
