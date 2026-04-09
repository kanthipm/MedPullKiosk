package com.medpull.kiosk.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medpull.kiosk.data.models.FormIntakeFlow
import com.medpull.kiosk.data.models.BranchingRule
import com.medpull.kiosk.data.models.BranchingActionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room entity for FormIntakeFlow
 */
@Entity(
    tableName = "form_intake_flows",
    foreignKeys = [
        ForeignKey(
            entity = FormEntity::class,
            parentColumns = ["id"],
            childColumns = ["formId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("formId")]
)
data class FormIntakeFlowEntity(
    @PrimaryKey
    val id: String,
    val formId: String,
    val currentQuestionIndex: Int = 0,
    val answeredFieldIds: String = "",   // JSON array
    val confirmedFieldIds: String = "",
    val inferredFieldIds: String = "",
    val skippedFieldIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): FormIntakeFlow {
        val gson = Gson()
        val arrayType = object : TypeToken<List<String>>() {}.type

        return FormIntakeFlow(
            id = id,
            formId = formId,
            currentQuestionIndex = currentQuestionIndex,
            answeredFieldIds = if (answeredFieldIds.isNotEmpty()) {
                gson.fromJson<List<String>>(answeredFieldIds, arrayType).toSet()
            } else emptySet(),
            confirmedFieldIds = if (confirmedFieldIds.isNotEmpty()) {
                gson.fromJson<List<String>>(confirmedFieldIds, arrayType).toSet()
            } else emptySet(),
            inferredFieldIds = if (inferredFieldIds.isNotEmpty()) {
                gson.fromJson<List<String>>(inferredFieldIds, arrayType).toSet()
            } else emptySet(),
            skippedFieldIds = if (skippedFieldIds.isNotEmpty()) {
                gson.fromJson<List<String>>(skippedFieldIds, arrayType).toSet()
            } else emptySet(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(flow: FormIntakeFlow): FormIntakeFlowEntity {
            val gson = Gson()
            return FormIntakeFlowEntity(
                id = flow.id,
                formId = flow.formId,
                currentQuestionIndex = flow.currentQuestionIndex,
                answeredFieldIds = gson.toJson(flow.answeredFieldIds.toList()),
                confirmedFieldIds = gson.toJson(flow.confirmedFieldIds.toList()),
                inferredFieldIds = gson.toJson(flow.inferredFieldIds.toList()),
                skippedFieldIds = gson.toJson(flow.skippedFieldIds.toList()),
                createdAt = flow.createdAt,
                updatedAt = flow.updatedAt
            )
        }
    }
}

/**
 * Room entity for BranchingRule
 */
@Entity(
    tableName = "branching_rules",
    foreignKeys = [
        ForeignKey(
            entity = FormEntity::class,
            parentColumns = ["id"],
            childColumns = ["formId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("formId"), Index("sourceFieldId")]
)
data class BranchingRuleEntity(
    @PrimaryKey
    val id: String,
    val formId: String,
    val sourceFieldId: String,
    val triggerValue: String,
    val actionType: String,
    val targetFieldIds: String = "",  // JSON array
    val priority: Int = 0
) {
    fun toDomain(): BranchingRule {
        val gson = Gson()
        val arrayType = object : TypeToken<List<String>>() {}.type

        return BranchingRule(
            id = id,
            formId = formId,
            sourceFieldId = sourceFieldId,
            triggerValue = triggerValue,
            actionType = BranchingActionType.valueOf(actionType),
            targetFieldIds = if (targetFieldIds.isNotEmpty()) {
                gson.fromJson(targetFieldIds, arrayType)
            } else emptyList(),
            priority = priority
        )
    }

    companion object {
        fun fromDomain(rule: BranchingRule): BranchingRuleEntity {
            val gson = Gson()
            return BranchingRuleEntity(
                id = rule.id,
                formId = rule.formId,
                sourceFieldId = rule.sourceFieldId,
                triggerValue = rule.triggerValue,
                actionType = rule.actionType.name,
                targetFieldIds = gson.toJson(rule.targetFieldIds),
                priority = rule.priority
            )
        }
    }
}
