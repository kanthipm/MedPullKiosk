from datetime import date


def test_health(client):
    body = client.get("/api/health").json()
    assert body["status"] == "ok" and body["db_ok"] is True
    assert body["llm_provider"] == "fallback"


def test_worklist_shape_and_ordering(client):
    body = client.get("/api/worklist").json()
    assert body["stats"]["total"] == 10
    assert body["briefing"]["text"]
    priorities = [p["priority"] for p in body["patients"]]
    order = {"high": 0, "medium": 1, "missing_data": 2, "low": 3}
    assert priorities == sorted(priorities, key=lambda p: order[p])
    assert body["patients"][0]["id"] == "marcus"
    row = body["patients"][0]
    for field in ("reason", "postop_day", "assigned_provider", "data_confidence", "trajectory"):
        assert field in row


def test_patient_detail(client):
    body = client.get("/api/patients/marcus").json()
    assert body["risk"]["level"] == "high"
    assert body["summary"]["text"].strip()
    assert 1 <= len(body["actions"]) <= 4
    assert body["rtm"]["window_days"] == 30
    assert body["device"]["provider"] == "apple"


def test_patient_404(client):
    assert client.get("/api/patients/nobody").status_code == 404


def test_metrics_endpoint(client):
    body = client.get("/api/patients/marcus/metrics").json()
    assert body["composite"]["level"] == "high"
    keys = {m["metric_key"] for m in body["metrics"]}
    assert "walking_asymmetry_pct" in keys  # apple patient gets gait cards
    body2 = client.get("/api/patients/linda/metrics").json()
    keys2 = {m["metric_key"] for m in body2["metrics"]}
    assert "walking_asymmetry_pct" not in keys2  # fitbit patient: no gait card


def test_timeline(client):
    events = client.get("/api/patients/marcus/timeline").json()["events"]
    kinds = [e["kind"] for e in events]
    assert kinds[0] == "surgery"
    assert kinds[-1] == "today"
    assert "change_point" in kinds


def test_checkins_newest_first(client):
    checkins = client.get("/api/patients/marcus/checkins").json()["checkins"]
    times = [c["occurred_at"] for c in checkins]
    assert times == sorted(times, reverse=True)
    assert checkins[0]["messages"][0]["who"] in ("patient", "copilot")


def test_observations_series(client):
    body = client.get("/api/patients/marcus/observations?metric_type=steps&days=30").json()
    assert body["unit"] == "count"
    assert len(body["points"]) > 10
    assert client.get("/api/patients/marcus/observations?metric_type=zzz").status_code == 400


def test_integrations(client):
    providers = client.get("/api/integrations").json()["providers"]
    assert len(providers) == 10
    by_key = {p["key"]: p for p in providers}
    assert by_key["mock"]["status"] == "mock_connected"
    assert by_key["apple"]["gait_capable"] is True
    assert by_key["fitbit"]["gait_capable"] is False
    assert client.post("/api/integrations/fitbit/connect").status_code == 501
    assert client.post("/api/integrations/nope/connect").status_code == 404


def test_webhook_flow(client):
    payload = {
        "patient_id": "elena",
        "provider": "apple",
        "records": [
            {"metric_type": "steps", "date": date.today().isoformat(), "value": 11000.0,
             "unit": "count"}
        ],
    }
    first = client.post("/api/webhooks/wearables/mock", json=payload).json()
    assert first["accepted"] is True
    replay = client.post("/api/webhooks/wearables/mock", json=payload).json()
    assert replay["duplicates"] >= 1
    assert client.post("/api/webhooks/wearables/nope", json={}).status_code == 404
    assert client.post("/api/webhooks/wearables/garmin", json={}).status_code == 501
    assert client.post("/api/webhooks/wearables/mock", json={"bad": 1}).status_code == 422


def test_notifications_flow(client):
    notifications = client.get("/api/notifications?status=all").json()["notifications"]
    assert any(n["kind"] == "priority_high" and n["patient_id"] == "marcus" for n in notifications)
    target = notifications[0]
    assert client.post(f"/api/notifications/{target['id']}/read").json()["ok"] is True
    assert client.post("/api/notifications/read-all").json()["ok"] is True


def test_notification_preferences_roundtrip(client):
    prefs = client.get("/api/notification-preferences").json()
    channels = {p["channel"]: p for p in prefs}
    assert channels["in_app"]["available"] is True
    assert channels["sms"]["available"] is False
    updated = client.put(
        "/api/notification-preferences",
        json=[{"channel": "in_app", "enabled": False, "min_priority": "high"}],
    ).json()
    assert {p["channel"]: p for p in updated}["in_app"]["enabled"] is False
    client.put(
        "/api/notification-preferences",
        json=[{"channel": "in_app", "enabled": True, "min_priority": "high"}],
    )


def test_recompute(client):
    body = client.post("/api/patients/sofia/recompute").json()
    assert body["risk_level"] == "medium"
