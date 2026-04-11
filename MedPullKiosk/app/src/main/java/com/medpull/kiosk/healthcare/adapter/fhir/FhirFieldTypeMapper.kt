package com.medpull.kiosk.healthcare.adapter.fhir

import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.healthcare.models.QuestionnaireItemType
import org.hl7.fhir.r4.model.Questionnaire
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps between app FieldType, neutral QuestionnaireItemType, and FHIR Questionnaire item types.
 */
@Singleton
class FhirFieldTypeMapper @Inject constructor() {

    fun fieldTypeToQuestionnaireItemType(fieldType: FieldType): QuestionnaireItemType {
        return when (fieldType) {
            FieldType.TEXT -> QuestionnaireItemType.STRING
            FieldType.NUMBER -> QuestionnaireItemType.INTEGER
            FieldType.DATE -> QuestionnaireItemType.DATE
            FieldType.CHECKBOX -> QuestionnaireItemType.BOOLEAN
            FieldType.RADIO -> QuestionnaireItemType.CHOICE
            FieldType.SIGNATURE -> QuestionnaireItemType.ATTACHMENT
            FieldType.DROPDOWN -> QuestionnaireItemType.CHOICE
            FieldType.MULTI_SELECT -> QuestionnaireItemType.CHOICE
            FieldType.STATIC_LABEL -> QuestionnaireItemType.DISPLAY
            FieldType.UNKNOWN -> QuestionnaireItemType.STRING
        }
    }

    fun questionnaireItemTypeToFieldType(type: QuestionnaireItemType): FieldType {
        return when (type) {
            QuestionnaireItemType.STRING, QuestionnaireItemType.TEXT,
            QuestionnaireItemType.URL -> FieldType.TEXT
            QuestionnaireItemType.INTEGER, QuestionnaireItemType.DECIMAL,
            QuestionnaireItemType.QUANTITY -> FieldType.NUMBER
            QuestionnaireItemType.DATE, QuestionnaireItemType.DATE_TIME,
            QuestionnaireItemType.TIME -> FieldType.DATE
            QuestionnaireItemType.BOOLEAN -> FieldType.CHECKBOX
            QuestionnaireItemType.CHOICE, QuestionnaireItemType.OPEN_CHOICE -> FieldType.DROPDOWN
            QuestionnaireItemType.DISPLAY -> FieldType.STATIC_LABEL
            QuestionnaireItemType.ATTACHMENT -> FieldType.SIGNATURE
            QuestionnaireItemType.GROUP, QuestionnaireItemType.REFERENCE -> FieldType.UNKNOWN
        }
    }

    fun toFhirItemType(type: QuestionnaireItemType): Questionnaire.QuestionnaireItemType {
        return when (type) {
            QuestionnaireItemType.GROUP -> Questionnaire.QuestionnaireItemType.GROUP
            QuestionnaireItemType.DISPLAY -> Questionnaire.QuestionnaireItemType.DISPLAY
            QuestionnaireItemType.BOOLEAN -> Questionnaire.QuestionnaireItemType.BOOLEAN
            QuestionnaireItemType.DECIMAL -> Questionnaire.QuestionnaireItemType.DECIMAL
            QuestionnaireItemType.INTEGER -> Questionnaire.QuestionnaireItemType.INTEGER
            QuestionnaireItemType.DATE -> Questionnaire.QuestionnaireItemType.DATE
            QuestionnaireItemType.DATE_TIME -> Questionnaire.QuestionnaireItemType.DATETIME
            QuestionnaireItemType.TIME -> Questionnaire.QuestionnaireItemType.TIME
            QuestionnaireItemType.STRING -> Questionnaire.QuestionnaireItemType.STRING
            QuestionnaireItemType.TEXT -> Questionnaire.QuestionnaireItemType.TEXT
            QuestionnaireItemType.URL -> Questionnaire.QuestionnaireItemType.URL
            QuestionnaireItemType.CHOICE -> Questionnaire.QuestionnaireItemType.CHOICE
            QuestionnaireItemType.OPEN_CHOICE -> Questionnaire.QuestionnaireItemType.OPENCHOICE
            QuestionnaireItemType.ATTACHMENT -> Questionnaire.QuestionnaireItemType.ATTACHMENT
            QuestionnaireItemType.REFERENCE -> Questionnaire.QuestionnaireItemType.REFERENCE
            QuestionnaireItemType.QUANTITY -> Questionnaire.QuestionnaireItemType.QUANTITY
        }
    }

    fun fromFhirItemType(fhirType: Questionnaire.QuestionnaireItemType): QuestionnaireItemType {
        return when (fhirType) {
            Questionnaire.QuestionnaireItemType.GROUP -> QuestionnaireItemType.GROUP
            Questionnaire.QuestionnaireItemType.DISPLAY -> QuestionnaireItemType.DISPLAY
            Questionnaire.QuestionnaireItemType.BOOLEAN -> QuestionnaireItemType.BOOLEAN
            Questionnaire.QuestionnaireItemType.DECIMAL -> QuestionnaireItemType.DECIMAL
            Questionnaire.QuestionnaireItemType.INTEGER -> QuestionnaireItemType.INTEGER
            Questionnaire.QuestionnaireItemType.DATE -> QuestionnaireItemType.DATE
            Questionnaire.QuestionnaireItemType.DATETIME -> QuestionnaireItemType.DATE_TIME
            Questionnaire.QuestionnaireItemType.TIME -> QuestionnaireItemType.TIME
            Questionnaire.QuestionnaireItemType.STRING -> QuestionnaireItemType.STRING
            Questionnaire.QuestionnaireItemType.TEXT -> QuestionnaireItemType.TEXT
            Questionnaire.QuestionnaireItemType.URL -> QuestionnaireItemType.URL
            Questionnaire.QuestionnaireItemType.CHOICE -> QuestionnaireItemType.CHOICE
            Questionnaire.QuestionnaireItemType.OPENCHOICE -> QuestionnaireItemType.OPEN_CHOICE
            Questionnaire.QuestionnaireItemType.ATTACHMENT -> QuestionnaireItemType.ATTACHMENT
            Questionnaire.QuestionnaireItemType.REFERENCE -> QuestionnaireItemType.REFERENCE
            Questionnaire.QuestionnaireItemType.QUANTITY -> QuestionnaireItemType.QUANTITY
            else -> QuestionnaireItemType.STRING
        }
    }
}
