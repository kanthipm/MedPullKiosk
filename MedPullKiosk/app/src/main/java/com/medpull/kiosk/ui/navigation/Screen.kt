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
    object Export : Screen("export/{formId}") {
        fun createRoute(formId: String) = "export/$formId"
    }
    object FhirSettings : Screen("fhir_settings")
    object FhirImport : Screen("fhir_import/{formId}") {
        fun createRoute(formId: String) = "fhir_import/$formId"
    }
}
