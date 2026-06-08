package com.medpull.kiosk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medpull.kiosk.data.local.entities.CopilotAuditEntity

/**
 * DAO for the on-device AI co-pilot audit log.
 *
 * TODO(medpull-PHI): on-device only — TEMPORARY for testing. No S3 sync path
 * exists for this table by design. See [CopilotAuditEntity] and KNOWN_ISSUES.md.
 */
@Dao
interface CopilotAuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CopilotAuditEntity)

    @Query("SELECT * FROM copilot_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<CopilotAuditEntity>

    @Query("SELECT * FROM copilot_audit_logs WHERE formId = :formId ORDER BY timestamp ASC")
    suspend fun getForForm(formId: String): List<CopilotAuditEntity>

    /** Latencies of model-backed decisions — for computing p95/p99 to re-tune the timeout. */
    @Query("SELECT latencyMs FROM copilot_audit_logs WHERE outcome = 'decided' ORDER BY latencyMs ASC")
    suspend fun getDecidedLatencies(): List<Long>

    @Query("SELECT COUNT(*) FROM copilot_audit_logs")
    suspend fun count(): Int

    @Query("DELETE FROM copilot_audit_logs")
    suspend fun clear()
}
