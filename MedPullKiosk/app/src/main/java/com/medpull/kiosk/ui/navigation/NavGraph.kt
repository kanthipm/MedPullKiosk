package com.medpull.kiosk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.medpull.kiosk.ui.components.SessionTimeoutDialog
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.medpull.kiosk.ui.screens.auth.LoginScreen
import com.medpull.kiosk.ui.screens.auth.RegisterScreen
import com.medpull.kiosk.ui.screens.auth.VerificationScreen
import com.medpull.kiosk.healthcare.ui.FhirSettingsScreen
import com.medpull.kiosk.ui.screens.export.ExportScreen
import com.medpull.kiosk.ui.screens.formselection.FormSelectionScreen
import com.medpull.kiosk.ui.screens.formfill.FormFillScreen
import com.medpull.kiosk.ui.screens.intake.DocumentUploadScreen
import com.medpull.kiosk.ui.screens.intake.EligibilitySummaryScreen
import com.medpull.kiosk.ui.screens.intake.GuidedIntakeScreen
import com.medpull.kiosk.ui.screens.intake.FilledFormPreviewScreen
import com.medpull.kiosk.ui.screens.intake.IntakeReviewScreen
import com.medpull.kiosk.ui.screens.inventory.InventoryScreen
import com.medpull.kiosk.ui.screens.language.LanguageSelectionScreen
import com.medpull.kiosk.data.models.IntakeProgramType
import com.medpull.kiosk.ui.screens.intake.GuidedIntakeViewModel
import com.medpull.kiosk.ui.screens.programselection.ProgramSelectionScreen
import com.medpull.kiosk.ui.screens.welcome.WelcomeScreen
import com.medpull.kiosk.ui.screens.welcome.WelcomeViewModel
import com.medpull.kiosk.ui.screens.snfadmissions.SNFAdmissionsScreen
import com.medpull.kiosk.ui.screens.staff.StaffPortalScreen
import com.medpull.kiosk.utils.SessionManager
import com.medpull.kiosk.utils.SessionState
import com.medpull.kiosk.utils.SubmissionStore

/**
 * Navigation graph for the app
 * Manages screen navigation and session timeout
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    sessionManager: SessionManager,
    submissionStore: SubmissionStore
) {
    // Monitor session state
    val sessionState by sessionManager.sessionState.collectAsState()
    val remainingTime by sessionManager.remainingTime.collectAsState()

    var showTimeoutWarning by remember { mutableStateOf(false) }

    // Handle session state changes
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.Warning -> {
                showTimeoutWarning = true
            }
            is SessionState.Expired -> {
                showTimeoutWarning = false
                // Navigate back to welcome screen on timeout
                navController.navigate(Screen.Welcome.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is SessionState.Active -> {
                showTimeoutWarning = false
            }
            else -> {}
        }
    }

    // Show timeout warning dialog
    if (showTimeoutWarning) {
        SessionTimeoutDialog(
            remainingTimeMs = remainingTime,
            formatTime = { sessionManager.formatRemainingTime(it) },
            onContinue = {
                sessionManager.recordActivity()
                showTimeoutWarning = false
            },
            onLogout = {
                sessionManager.expireSession()
                showTimeoutWarning = false
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Screen.Language.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onStaffView = {
                    navController.navigate(Screen.StaffPortal.route)
                }
            )
        }

        composable(Screen.ProgramSelection.route) {
            ProgramSelectionScreen(
                onProgramSelected = { programType ->
                    when (programType) {
                        IntakeProgramType.SLIDING_FEE -> {
                            navController.navigate(
                                Screen.GuidedIntake.createRoute(GuidedIntakeViewModel.SLIDING_FEE_ID)
                            ) {
                                popUpTo(Screen.ProgramSelection.route)
                            }
                        }
                        IntakeProgramType.MEDICAL_INTAKE -> {
                            navController.navigate(Screen.FormSelection.route) {
                                popUpTo(Screen.ProgramSelection.route)
                            }
                        }
                        IntakeProgramType.SNF_ADMISSIONS -> {
                            navController.navigate(Screen.SNFAdmissions.route)
                        }
                    }
                },
                onStaffView = {
                    navController.navigate(Screen.StaffPortal.route)
                }
            )
        }

        composable(Screen.Language.route) {
            LanguageSelectionScreen(
                onLanguageSelected = {
                    navController.navigate(Screen.ProgramSelection.route) {
                        popUpTo(Screen.Language.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ProgramSelection.route) {
                        // Clear back stack to prevent back navigation to auth
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToVerification = { email ->
                    navController.navigate(Screen.Verification.createRoute(email))
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.ProgramSelection.route) {
                        // Clear back stack to prevent back navigation to auth
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onNavigateToVerification = { email ->
                    navController.navigate(Screen.Verification.createRoute(email))
                }
            )
        }

        composable(
            route = Screen.Verification.route,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            VerificationScreen(
                email = email,
                onVerificationSuccess = {
                    navController.navigate(Screen.ProgramSelection.route) {
                        // Clear back stack to prevent back navigation to auth
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.FormSelection.route) {
            FormSelectionScreen(
                sessionManager = sessionManager,
                onLogout = {
                    // Stop session on logout
                    sessionManager.stopSession()
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onFormSelected = { formId ->
                    navController.navigate(Screen.GuidedIntake.createRoute(formId))
                },
                onNavigateToInventory = {
                    navController.navigate(Screen.Inventory.route)
                }
            )
        }

        composable(Screen.FormFill.route) {
            FormFillScreen(
                sessionManager = sessionManager,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onExport = { formId ->
                    navController.navigate(Screen.Export.createRoute(formId))
                }
            )
        }

        composable(
            route = Screen.GuidedIntake.route,
            arguments = listOf(
                navArgument("formId") { type = NavType.StringType },
                navArgument("editLast") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId") ?: ""
            GuidedIntakeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onComplete = {
                    if (formId == GuidedIntakeViewModel.SLIDING_FEE_ID) {
                        navController.navigate(Screen.DocumentUpload.createRoute(formId)) {
                            popUpTo(Screen.ProgramSelection.route)
                        }
                    } else {
                        navController.navigate(Screen.IntakeReview.createRoute(formId)) {
                            popUpTo(Screen.FormSelection.route)
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.DocumentUpload.route,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId") ?: ""
            DocumentUploadScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = {
                    navController.navigate(Screen.EligibilitySummary.createRoute(formId)) {
                        popUpTo(Screen.ProgramSelection.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EligibilitySummary.route,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId") ?: ""
            EligibilitySummaryScreen(
                onEditInfo = {
                    navController.navigate(
                        Screen.GuidedIntake.createRoute(formId, editLast = true)
                    ) {
                        popUpTo(Screen.ProgramSelection.route)
                    }
                },
                onReuploadDocs = {
                    navController.navigate(Screen.DocumentUpload.createRoute(formId)) {
                        popUpTo(Screen.EligibilitySummary.createRoute(formId)) { inclusive = true }
                    }
                },
                onSubmitted = {
                    navController.navigate(Screen.FilledFormPreview.createRoute(formId)) {
                        popUpTo(Screen.ProgramSelection.route)
                    }
                }
            )
        }

        composable(
            route = Screen.IntakeReview.route,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId") ?: ""
            IntakeReviewScreen(
                onNavigateBack = {
                    navController.navigate(Screen.GuidedIntake.createRoute(formId, editLast = true)) {
                        popUpTo(Screen.ProgramSelection.route)
                    }
                },
                onSubmit = {
                    navController.navigate(Screen.FilledFormPreview.createRoute(formId)) {
                        popUpTo(Screen.FormSelection.route)
                    }
                }
            )
        }

        composable(
            route = Screen.FilledFormPreview.route,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) {
            FilledFormPreviewScreen(
                onDone = {
                    navController.navigate(Screen.ProgramSelection.route) {
                        popUpTo(Screen.ProgramSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Export.route) {
            ExportScreen(
                sessionManager = sessionManager,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Inventory.route) {
            InventoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.FhirSettings.route) {
            FhirSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.StaffPortal.route) {
            StaffPortalScreen(
                onBack = { navController.popBackStack() },
                submissionStore = submissionStore
            )
        }

        composable(Screen.SNFAdmissions.route) {
            SNFAdmissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
