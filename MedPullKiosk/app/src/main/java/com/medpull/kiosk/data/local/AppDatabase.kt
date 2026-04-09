package com.medpull.kiosk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.dao.FhirMappingDao
import com.medpull.kiosk.data.local.dao.FormDao
import com.medpull.kiosk.data.local.dao.FormFieldDao
import com.medpull.kiosk.data.local.dao.SyncQueueDao
import com.medpull.kiosk.data.local.dao.UserDao
import com.medpull.kiosk.data.local.dao.FormIntakeFlowDao
import com.medpull.kiosk.data.local.dao.BranchingRuleDao
import com.medpull.kiosk.data.local.entities.AuditLogEntity
import com.medpull.kiosk.data.local.entities.FhirMappingEntity
import com.medpull.kiosk.data.local.entities.FormEntity
import com.medpull.kiosk.data.local.entities.FormFieldEntity
import com.medpull.kiosk.data.local.entities.SyncQueueEntity
import com.medpull.kiosk.data.local.entities.UserEntity
import com.medpull.kiosk.data.local.entities.FormIntakeFlowEntity
import com.medpull.kiosk.data.local.entities.BranchingRuleEntity

/**
 * Room database for MedPull Kiosk
 * Provides offline storage for users, forms, audit logs, sync queue, and FHIR mappings
 */
@Database(
    entities = [
        UserEntity::class,
        FormEntity::class,
        FormFieldEntity::class,
        AuditLogEntity::class,
        SyncQueueEntity::class,
        FhirMappingEntity::class,
        FormIntakeFlowEntity::class,
        BranchingRuleEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun formDao(): FormDao
    abstract fun formFieldDao(): FormFieldDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun fhirMappingDao(): FhirMappingDao
    abstract fun formIntakeFlowDao(): FormIntakeFlowDao
    abstract fun branchingRuleDao(): BranchingRuleDao
}
