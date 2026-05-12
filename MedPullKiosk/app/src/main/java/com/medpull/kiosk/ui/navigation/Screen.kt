package com.medpull.kiosk.ui.navigation

/**
 * Sealed class for navigation routes
 */
sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Language : Screen("language")
    object Login : Screen("login")
    object Register : Screen("register")
    object Verification : Screen("verification/{email}") {
        fun createRoute(email: String) = "verification/$email"
    }
    object FormSelection : Screen("form_selection")
    object FormFill : Screen("form_fill/{formId}") {
        fun createRoute(formId: String) = "form_fill/$formId"
    }
    object GuidedIntake : Screen("guided_intake/{formId}?editLast={editLast}") {
        fun createRoute(formId: String, editLast: Boolean = false) =
            "guided_intake/$formId?editLast=$editLast"
    }
    object IntakeReview : Screen("intake_review/{formId}") {
        fun createRoute(formId: String) = "intake_review/$formId"
    }
    object FilledFormPreview : Screen("filled_form_preview/{formId}") {
        fun createRoute(formId: String) = "filled_form_preview/$formId"
    }
    object Export : Screen("export/{formId}") {
        fun createRoute(formId: String) = "export/$formId"
    }
    object FhirSettings : Screen("fhir_settings")
    object FhirImport : Screen("fhir_import/{formId}") {
        fun createRoute(formId: String) = "fhir_import/$formId"
    }
    object Inventory : Screen("inventory")
    object ProgramSelection : Screen("program_selection")
    object DocumentUpload : Screen("document_upload/{formId}") {
        fun createRoute(formId: String) = "document_upload/$formId"
    }
    object EligibilitySummary : Screen("eligibility_summary/{formId}") {
        fun createRoute(formId: String) = "eligibility_summary/$formId"
    }
}
