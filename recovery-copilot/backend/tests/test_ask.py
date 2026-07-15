from app.llm.ask import _roster_context, ask, fallback_ask
from app.llm.insights import BANNED


def test_ask_fever_finds_marcus(db):
    result = ask(db, "Who has a fever or elevated temperature?")
    assert "marcus" in result["patient_ids"]
    assert "Marcus" in result["answer"]
    assert result["provider"] == "fallback"


def test_ask_behind_schedule(db):
    result = ask(db, "Which patients are behind schedule?")
    assert set(result["patient_ids"]) >= {"sofia", "aisha"}


def test_ask_missing_data(db):
    result = ask(db, "Anyone with device data gaps?")
    assert result["patient_ids"] == ["priya"]


def test_ask_procedure_filter(db):
    result = ask(db, "Show me my hip patients")
    assert set(result["patient_ids"]) >= {"aisha", "grace"}
    assert all(pid in {"aisha", "grace", "priya"} for pid in result["patient_ids"])


def test_ask_unmatchable_question_is_honest(db):
    roster = _roster_context(db)
    result = fallback_ask("What's the weather like?", roster)
    assert result["patient_ids"] == []
    assert "couldn't match" in result["answer"]


def test_ask_never_uses_banned_language(db):
    for question in ("who has a fever", "who is behind", "who needs review"):
        result = ask(db, question)
        assert not BANNED.search(result["answer"])


def test_ask_is_cached(db):
    first = ask(db, "who has a fever?")
    second = ask(db, "who has a fever?")
    assert first["generated_at"] == second["generated_at"]


def test_ask_endpoint(client):
    response = client.post("/api/ask", json={"question": "Who reported fever this week?"})
    assert response.status_code == 200
    body = response.json()
    assert "marcus" in body["patient_ids"]
    assert client.post("/api/ask", json={"question": "hi"}).status_code == 422


def test_draft_message_endpoint(client):
    body = client.post("/api/patients/marcus/actions/draft-message").json()
    assert 20 <= len(body["message"]) <= 320
    assert not BANNED.search(body["message"])
    assert "Marcus" in body["message"]
    # temperature is marcus's top signal — the fallback draft should ask about it
    assert "temperature" in body["message"].lower()
