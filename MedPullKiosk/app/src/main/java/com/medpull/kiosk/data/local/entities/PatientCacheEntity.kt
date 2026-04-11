package com.medpull.kiosk.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches 11 demographic fields per user after a completed intake.
 * Used to pre-fill the next intake so returning patients don't re-enter contact info.
 */
@Entity(tableName = "patient_cache")
data class PatientCacheEntity(
    @PrimaryKey val userId: String,
    val patientFullName: String? = null,
    val dateOfBirth: String? = null,
    val mailingStreet: String? = null,
    val mailingCity: String? = null,
    val mailingState: String? = null,
    val mailingZip: String? = null,
    val cellPhone: String? = null,
    val email: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val emergencyContactRelationship: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun valueForFieldId(id: String): String? = when (id) {
        "patient_full_name"              -> patientFullName
        "date_of_birth"                  -> dateOfBirth
        "mailing_address_street"         -> mailingStreet
        "mailing_city"                   -> mailingCity
        "mailing_state"                  -> mailingState
        "mailing_zip"                    -> mailingZip
        "cell_phone"                     -> cellPhone
        "email"                          -> email
        "emergency_contact_name"         -> emergencyContactName
        "emergency_contact_phone"        -> emergencyContactPhone
        "emergency_contact_relationship" -> emergencyContactRelationship
        else                             -> null
    }

    companion object {
        val DEMOGRAPHIC_FIELD_IDS = setOf(
            "patient_full_name", "date_of_birth",
            "mailing_address_street", "mailing_city", "mailing_state", "mailing_zip",
            "cell_phone", "email",
            "emergency_contact_name", "emergency_contact_phone", "emergency_contact_relationship"
        )
    }
}
