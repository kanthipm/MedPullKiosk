package com.medpull.kiosk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medpull.kiosk.data.local.entities.FormIntakeFlowEntity
import com.medpull.kiosk.data.local.entities.BranchingRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for FormIntakeFlow
 */
@Dao
interface FormIntakeFlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flow: FormIntakeFlowEntity)

    @Query("SELECT * FROM form_intake_flows WHERE formId = :formId LIMIT 1")
    fun getByFormId(formId: String): Flow<FormIntakeFlowEntity?>

    @Query("SELECT * FROM form_intake_flows WHERE formId = :formId LIMIT 1")
    suspend fun getByFormIdOnce(formId: String): FormIntakeFlowEntity?

    @Query("DELETE FROM form_intake_flows WHERE formId = :formId")
    suspend fun deleteByFormId(formId: String)

    @Update
    suspend fun update(flow: FormIntakeFlowEntity)
}

/**
 * Data access object for BranchingRule
 */
@Dao
interface BranchingRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: BranchingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BranchingRuleEntity>)

    @Query("SELECT * FROM branching_rules WHERE formId = :formId ORDER BY priority ASC")
    fun getRulesByFormId(formId: String): Flow<List<BranchingRuleEntity>>

    @Query("SELECT * FROM branching_rules WHERE formId = :formId ORDER BY priority ASC")
    suspend fun getRulesByFormIdOnce(formId: String): List<BranchingRuleEntity>

    @Query("SELECT * FROM branching_rules WHERE formId = :formId AND sourceFieldId = :fieldId")
    suspend fun getRulesForField(formId: String, fieldId: String): List<BranchingRuleEntity>

    @Query("DELETE FROM branching_rules WHERE formId = :formId")
    suspend fun deleteByFormId(formId: String)
}
