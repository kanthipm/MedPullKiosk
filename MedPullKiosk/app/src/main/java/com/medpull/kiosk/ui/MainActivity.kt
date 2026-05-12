package com.medpull.kiosk.ui

import android.os.Bundle
import android.view.WindowManager
import com.medpull.kiosk.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.medpull.kiosk.ui.navigation.NavGraph
import com.medpull.kiosk.ui.theme.MedPullKioskTheme
import com.medpull.kiosk.utils.LocaleManager
import com.medpull.kiosk.utils.SessionManager
import com.medpull.kiosk.utils.SubmissionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity for MedPull Kiosk
 * Single activity architecture with Jetpack Compose
 * Handles activity lifecycle and session tracking
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var submissionStore: SubmissionStore

    @Inject
    lateinit var localeManager: LocaleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply security settings (prevent screenshots — disabled in debug for development)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContent {
            // Observe saved language and update the Activity's resource configuration.
            // Providing LocalConfiguration triggers recomposition so stringResource()
            // re-resolves against the Activity's updated resources.
            val language by localeManager.getLanguageFlow(this@MainActivity)
                .collectAsState(initial = "en")
            val updatedConfig = remember(language) {
                val localizedCtx = localeManager.applyLocale(this@MainActivity, language)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(
                    localizedCtx.resources.configuration,
                    resources.displayMetrics
                )
                localizedCtx.resources.configuration
            }

            CompositionLocalProvider(LocalConfiguration provides updatedConfig) {
                MedPullKioskTheme {
                    // Track activity lifecycle for session management
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    // Record activity when app comes to foreground
                                    sessionManager.recordActivity()
                                }
                                Lifecycle.Event.ON_PAUSE -> {
                                    // Session continues in background but no activity recorded
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph(sessionManager = sessionManager, submissionStore = submissionStore)
                    }
                }
            }
        }
    }
}
