package com.medpull.kiosk.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-device audit record of one AI co-pilot decision.
 *
 * TODO(medpull-PHI): on-device logging only — TEMPORARY for testing. This table
 * stores patient answers (PHI: rawAnswer / normalizedAnswer) in plaintext on the
 * device and is deliberately NOT wired to the S3 audit sync. Revisit storage
 * location, retention, encryption, and HIPAA/compliance before production. This is
 * a known production blocker — see KNOWN_ISSUES.md.
 *
 * Distinct from [AuditLogEntity] precisely so it stays off the cloud sync path.
 * `confidence` is the model's self-reported (uncalibrated) score, kept so thresholds
 * can be calibrated against actual answer-correctness; `latencyMs` is kept so the
 * timeout can be re-tuned from observed p95/p99.
 */
@Entity(tableName = "copilot_audit_logs")
data class CopilotAuditEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val formId: String?,
    val fieldId: String,
    /** PHI — what the patient said, verbatim. */
    val rawAnswer: String,
    /** PHI — the normalized value the model proposed (null if none). */
    val normalizedAnswer: String?,
    val assessment: String,
    /** Model self-reported confidence — UNCALIBRATED; logged for calibration. */
    val confidence: Float,
    val action: String,
    val interventionType: String?,
    /** Patient-facing intervention text shown/spoken (null when accepted silently). */
    val message: String?,
    /** decided | unreachable | model_unavailable */
    val outcome: String,
    val latencyMs: Long,
    /** JSON of also_fills the model proposed (inferred values for un-answered fields). */
    val alsoFillsProposed: String?,
    /** True when also_fills were actually written — those values are INFERRED, not patient-stated. */
    val alsoFillsApplied: Boolean = false
)
