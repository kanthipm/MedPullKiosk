"""Idempotent persistence of canonical observations.

Wearable providers re-deliver webhooks, revise already-delivered rows
(restatement — Apple resting HR, Garmin sleep staging, WHOOP edits are all
routine) and back-fill late data. First-write-wins is therefore wrong in a way
that biases the engine toward under-reporting: a provisional partial value
would win forever over the provider's corrected one. Ingestion is a genuine
upsert:

* a byte-identical redelivery (same payload_hash) counts as a duplicate;
* a changed payload updates the row and bumps ``revision`` — unless the
  provider's own ``source_updated_at`` says the incoming copy is older than
  what we hold, which counts as a duplicate (out-of-order redelivery);
* a provider tombstone (``deleted=True``) soft-deletes: the row keeps its
  identity so the deletion survives re-delivery, but every reader filters
  ``deleted_at``.
"""

import hashlib
import json
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.connectors.base import CanonicalObservation
from app.models.observation import Observation


def payload_hash_of(o: CanonicalObservation) -> str:
    """Content hash over the fields a restatement can change."""
    material = json.dumps(
        {
            "value_num": o.value_num,
            "value_json": o.value_json,
            "unit": o.unit,
            "start": o.start_time.isoformat(),
            "end": o.end_time.isoformat(),
            "deleted": o.deleted,
        },
        sort_keys=True,
        default=str,
    )
    return hashlib.sha256(material.encode()).hexdigest()


def _apply(row: Observation, o: CanonicalObservation, content_hash: str) -> None:
    row.value_num = o.value_num
    row.value_json = o.value_json
    row.unit = o.unit
    row.start_time = o.start_time
    row.end_time = o.end_time
    row.timezone = o.timezone
    row.local_date = o.local_date
    row.source_device_id = o.source_device_id
    row.body_site = o.body_site
    row.side = o.side
    row.source_updated_at = o.source_updated_at
    row.payload_hash = content_hash
    row.raw_payload = o.raw_payload
    row.deleted_at = datetime.now() if o.deleted else None
    row.ingested_at = datetime.now()


def ingest_observations(
    db: Session, observations: list[CanonicalObservation]
) -> tuple[int, int, int]:
    """Returns (ingested, updated, duplicates)."""
    if not observations:
        return (0, 0, 0)

    keys = [o.dedupe_key for o in observations]
    existing: dict[str, Observation] = {
        row.dedupe_key: row
        for row in db.scalars(
            select(Observation).where(Observation.dedupe_key.in_(keys))
        )
    }

    ingested = 0
    updated = 0
    duplicates = 0
    for o in observations:
        key = o.dedupe_key
        content_hash = payload_hash_of(o)
        row = existing.get(key)

        if row is None:
            row = Observation(
                patient_id=o.patient_id,
                source_provider=o.source_provider,
                source_device_id=o.source_device_id,
                metric_type=o.metric_type,
                unit=o.unit,
                value_num=o.value_num,
                value_json=o.value_json,
                start_time=o.start_time,
                end_time=o.end_time,
                timezone=o.timezone,
                local_date=o.local_date,
                granularity=o.granularity,
                body_site=o.body_site,
                side=o.side,
                external_id=o.external_id,
                source_updated_at=o.source_updated_at,
                payload_hash=content_hash,
                deleted_at=datetime.now() if o.deleted else None,
                qualifies_for_rtm=o.qualifies_for_rtm,
                is_patient_reported=o.is_patient_reported,
                dedupe_key=key,
                raw_payload=o.raw_payload,
            )
            db.add(row)
            existing[key] = row  # an intra-batch repeat is a restatement, not an insert
            ingested += 1
            continue

        if row.payload_hash == content_hash and bool(row.deleted_at) == o.deleted:
            duplicates += 1
            continue

        # Out-of-order redelivery: the provider's own clock says our copy is
        # newer. payload_hash alone can't catch this — the stale copy differs.
        if (
            o.source_updated_at is not None
            and row.source_updated_at is not None
            and o.source_updated_at <= row.source_updated_at
        ):
            duplicates += 1
            continue

        _apply(row, o, content_hash)
        row.revision = (row.revision or 0) + 1
        updated += 1

    db.commit()
    return (ingested, updated, duplicates)
