package com.medpull.kiosk.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ClinicSubmission(
    val id: String,
    val submittedAt: String,
    val status: String,
    val personal: SubmissionPersonal,
    val household: SubmissionHousehold,
    val financial: SubmissionFinancial,
    val documents: List<SubmissionDocument>,
    val missingDocumentsCount: Int,
    val missingRequiredFields: List<String>
)

data class SubmissionPersonal(
    val fullName: String,
    val dob: String,
    val address: String,
    val phone: String?,
    val email: String?
)

data class SubmissionHousehold(
    val householdSize: Int,
    val dependents: Int
)

data class SubmissionFinancial(
    val incomeSources: List<String>,
    val monthlyIncome: Double,
    val employmentStatus: String,
    val insuranceStatus: String
)

data class SubmissionDocument(
    val id: String,
    val type: String,
    val fileName: String,
    val uploadStatus: String,
    val uploadedAt: String?
)

@Singleton
class SubmissionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder().create()
    private val file: File get() = File(context.filesDir, "clinic_submissions.json")

    fun save(submission: ClinicSubmission) {
        val existing = loadAll().toMutableList()
        // Replace if same formId (re-send), otherwise prepend
        val idx = existing.indexOfFirst { it.id == submission.id }
        if (idx >= 0) existing[idx] = submission else existing.add(0, submission)
        file.writeText(gson.toJson(existing))
    }

    fun loadAllJson(): String =
        if (file.exists()) file.readText() else "[]"

    private fun loadAll(): List<ClinicSubmission> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<ClinicSubmission>>() {}.type
            gson.fromJson<List<ClinicSubmission>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
