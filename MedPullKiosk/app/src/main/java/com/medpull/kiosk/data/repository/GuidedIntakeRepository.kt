package com.medpull.kiosk.data.repository

import android.util.Log
import com.medpull.kiosk.data.local.dao.FormIntakeFlowDao
import com.medpull.kiosk.data.local.dao.BranchingRuleDao
import com.medpull.kiosk.data.local.dao.FormFieldDao
import com.medpull.kiosk.data.local.entities.FormIntakeFlowEntity
import com.medpull.kiosk.data.local.entities.BranchingRuleEntity
import com.medpull.kiosk.data.models.FormIntakeFlow
import com.medpull.kiosk.data.models.BranchingRule
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.IntakeQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Repository for guided intake flow operations
 * Manages conversational form filling state, branching rules, and question progression
 */
@Singleton
class GuidedIntakeRepository @Inject constructor(
    private val intakeFlowDao: FormIntakeFlowDao,
    private val branchingRuleDao: BranchingRuleDao,
    private val formFieldDao: FormFieldDao
) {

    companion object {
        private const val TAG = "GuidedIntakeRepository"
    }

    /**
     * Get or create intake flow for a form
     */
    suspend fun getOrCreateFlow(formId: String): FormIntakeFlow {
        val existing = intakeFlowDao.getByFormIdOnce(formId)
        return if (existing != null) {
            existing.toDomain()
        } else {
            val newFlow = FormIntakeFlow(
                id = UUID.randomUUID().toString(),
                formId = formId
            )
            intakeFlowDao.upsert(FormIntakeFlowEntity.fromDomain(newFlow))
            newFlow
        }
    }

    /**
     * Watch intake flow changes as Flow
     */
    fun watchFlow(formId: String): Flow<FormIntakeFlow?> {
        return intakeFlowDao.getByFormId(formId).map { it?.toDomain() }
    }

    /**
     * Update intake flow state
     */
    suspend fun updateFlow(flow: FormIntakeFlow) {
        intakeFlowDao.update(
            FormIntakeFlowEntity.fromDomain(
                flow.copy(updatedAt = System.currentTimeMillis())
            )
        )
    }

    /**
     * Mark a field as answered in the intake flow
     */
    suspend fun markFieldAnswered(formId: String, fieldId: String) {
        val entity = intakeFlowDao.getByFormIdOnce(formId)?.toDomain() ?: return
        updateFlow(entity.copy(answeredFieldIds = entity.answeredFieldIds + fieldId))
        Log.d(TAG, "Marked field answered: $fieldId")
    }

    /**
     * Mark a field as confirmed (user approved the inferred value)
     */
    suspend fun markFieldConfirmed(formId: String, fieldId: String) {
        val entity = intakeFlowDao.getByFormIdOnce(formId)?.toDomain() ?: return
        updateFlow(entity.copy(confirmedFieldIds = entity.confirmedFieldIds + fieldId))
        Log.d(TAG, "Marked field confirmed: $fieldId")
    }

    /**
     * Mark fields to skip based on branching logic
     */
    suspend fun markFieldsSkipped(formId: String, fieldIds: List<String>) {
        val entity = intakeFlowDao.getByFormIdOnce(formId)?.toDomain() ?: return
        updateFlow(entity.copy(skippedFieldIds = entity.skippedFieldIds + fieldIds))
        Log.d(TAG, "Marked ${fieldIds.size} fields skipped")
    }

    /**
     * Mark fields as inferred by AI
     */
    suspend fun markFieldsInferred(formId: String, fieldIds: List<String>) {
        val entity = intakeFlowDao.getByFormIdOnce(formId)?.toDomain() ?: return
        updateFlow(entity.copy(inferredFieldIds = entity.inferredFieldIds + fieldIds))
        Log.d(TAG, "Marked ${fieldIds.size} fields inferred")
    }

    /**
     * Set current question index
     */
    suspend fun setCurrentQuestion(formId: String, index: Int) {
        val entity = intakeFlowDao.getByFormIdOnce(formId)?.toDomain() ?: return
        updateFlow(entity.copy(currentQuestionIndex = index))
    }

    /**
     * Generate intake questions from form fields
     * Creates question text for each fillable field
     */
    fun generateQuestionsFromFields(fields: List<FormField>): List<IntakeQuestion> {
        return fields
            .filter { it.fieldType != FieldType.STATIC_LABEL }
            .map { field ->
                IntakeQuestion(
                    fieldId = field.id,
                    question = generateQuestionText(field),
                    expectedFieldType = field.fieldType,
                    conversationalHints = field.translatedText ?: field.fieldName
                )
            }
    }

    /**
     * Public fallback — used by IntakeConversationEngine when Claude fails
     */
    fun generateFallbackQuestion(field: FormField): String = generateQuestionText(field)

    /**
     * Generate natural language question from field metadata
     */
    private fun generateQuestionText(field: FormField): String {
        val fieldLabel = field.translatedText ?: field.fieldName

        return when (field.fieldType) {
            FieldType.TEXT -> "What is your $fieldLabel?"
            FieldType.NUMBER -> "What is your $fieldLabel?"
            FieldType.DATE -> "What is your $fieldLabel?"
            FieldType.CHECKBOX -> "Do you have $fieldLabel?"
            FieldType.RADIO -> "What is your $fieldLabel?"
            FieldType.DROPDOWN -> "What is your $fieldLabel?"
            else -> "Please provide your $fieldLabel:"
        }
    }

    /**
     * Get branching rules for form
     */
    suspend fun getBranchingRules(formId: String): List<BranchingRule> {
        return branchingRuleDao.getRulesByFormIdOnce(formId).map { it.toDomain() }
    }

    /**
     * Get fields to skip based on branching rules
     */
    suspend fun getFieldsToSkip(
        formId: String,
        flow: FormIntakeFlow,
        fields: List<FormField>
    ): List<String> {
        val rules = getBranchingRules(formId)
        val fieldsToSkip = mutableSetOf<String>()

        for (rule in rules.sortedBy { it.priority }) {
            // Check if the source field has a value matching the trigger
            val sourceField = fields.find { it.id == rule.sourceFieldId }
            if (sourceField?.value?.lowercase() == rule.triggerValue.lowercase()) {
                // Apply action
                when (rule.actionType) {
                    com.medpull.kiosk.data.models.BranchingActionType.SKIP_FIELDS -> {
                        fieldsToSkip.addAll(rule.targetFieldIds)
                    }
                    com.medpull.kiosk.data.models.BranchingActionType.SHOW_FIELDS -> {
                        // Remove from skip list
                        fieldsToSkip.removeAll(rule.targetFieldIds)
                    }
                }
            }
        }

        return fieldsToSkip.toList()
    }

    /**
     * Get questions excluding skipped fields
     */
    suspend fun getActiveQuestions(
        formId: String,
        questions: List<IntakeQuestion>,
        flow: FormIntakeFlow
    ): List<IntakeQuestion> {
        val fieldsToSkip = flow.skippedFieldIds
        return questions.filter { it.fieldId !in fieldsToSkip }
    }

    /**
     * Clear intake flow (reset progress)
     */
    suspend fun clearFlow(formId: String) {
        intakeFlowDao.deleteByFormId(formId)
        Log.d(TAG, "Cleared intake flow for form: $formId")
    }

    /**
     * Insert branching rules for a form
     */
    suspend fun insertBranchingRules(rules: List<BranchingRule>) {
        val entities = rules.map { BranchingRuleEntity.fromDomain(it) }
        branchingRuleDao.insertAll(entities)
        Log.d(TAG, "Inserted ${entities.size} branching rules")
    }
}
