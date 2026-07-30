from datetime import date

import pytest

from app.connectors.apple_healthkit import AppleHealthKitConnector
from app.connectors.capabilities import CAPABILITIES, provider_supports
from app.connectors.ingest import ingest_observations
from app.connectors.junction import JunctionConnector
from app.connectors.mock import MockConnector
from app.connectors.registry import PROVIDERS, get_connector
from app.connectors.terra import TerraConnector
from app.models.enums import MetricType, SourceProvider


def _payload(patient_id="james", day=None):
    day = day or date.today().isoformat()
    return {
        "patient_id": patient_id,
        "provider": "fitbit",
        "records": [
            {"metric_type": "steps", "date": day, "value": 9100.0, "unit": "count"},
            {"metric_type": "resting_hr", "date": day, "value": 61.0, "unit": "bpm"},
        ],
    }


def test_mock_normalize_pinned_format():
    observations = MockConnector().normalize(_payload())
    assert len(observations) == 2
    assert observations[0].patient_id == "james"
    assert observations[0].source_provider == SourceProvider.FITBIT
    assert observations[0].granularity == "daily_summary"
    assert observations[0].dedupe_key.startswith("fitbit:james:steps:")


def test_mock_normalize_rejects_malformed():
    with pytest.raises(ValueError):
        MockConnector().normalize({"nope": True})


def test_ingest_is_idempotent(db):
    observations = MockConnector().normalize(_payload(day="2030-01-01"))
    first = ingest_observations(db, observations)
    second = ingest_observations(db, observations)
    assert first == (2, 0, 0)   # (ingested, updated, duplicates)
    assert second == (0, 0, 2)  # byte-identical redelivery is a duplicate, never an update


def test_registry_shape():
    assert len(PROVIDERS) == 10
    assert get_connector(SourceProvider.MOCK) is not None
    assert get_connector(SourceProvider.FITBIT) is None  # scaffolded only


def test_gait_capability_is_apple_only():
    gait = MetricType.WALKING_ASYMMETRY_PCT
    real_providers = [p for p in CAPABILITIES if p not in (SourceProvider.MOCK,)]
    supporting = {p for p in real_providers if provider_supports(p, gait)}
    assert supporting == {SourceProvider.APPLE}


def test_stub_connectors_raise_not_implemented():
    for connector in (TerraConnector(), JunctionConnector(), AppleHealthKitConnector()):
        with pytest.raises(NotImplementedError):
            connector.authorize("marcus")
        with pytest.raises(NotImplementedError):
            connector.normalize({})
