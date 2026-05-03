package com.medpull.kiosk.ui.screens.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

private data class WelcomeTranslation(val buttonText: String, val welcomeMessage: String)

private val welcomeTranslations = listOf(
    WelcomeTranslation("Get Started", "Welcome! Tap below to get started."),
    WelcomeTranslation("Comenzar", "¡Bienvenido! Toque abajo para comenzar."),
    WelcomeTranslation("开始", "欢迎！点击下方开始填写您的医疗表格。"),
    WelcomeTranslation("Commencer", "Bienvenue ! Appuyez ci-dessous pour commencer."),
    WelcomeTranslation("शुरू करें", "स्वागत है! शुरू करने के लिए नीचे टैप करें।"),
    WelcomeTranslation("ابدأ", "!مرحبًا! انقر أدناه للبدء"),
)

/**
 * Welcome screen - entry point of the app
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = hiltViewModel(),
    onContinue: () -> Unit,
    onDemoMode: () -> Unit = {}
) {
    var languageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L)
            languageIndex = (languageIndex + 1) % welcomeTranslations.size
        }
    }

    val fadeSpec = fadeIn(
        animationSpec = androidx.compose.animation.core.tween(600)
    ) togetherWith fadeOut(
        animationSpec = androidx.compose.animation.core.tween(600)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo/branding
        Text(
            text = "MedPull Kiosk",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Medical Form Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Welcome message — cycles through languages
        AnimatedContent(
            targetState = languageIndex,
            transitionSpec = { fadeSpec },
            label = "welcomeMessage"
        ) { index ->
            Text(
                text = welcomeTranslations[index].welcomeMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Continue button — button text cycles through languages
        Button(
            onClick = {
                viewModel.startSession()
                onContinue()
            },
            modifier = Modifier
                .width(300.dp)
                .height(64.dp)
        ) {
            AnimatedContent(
                targetState = languageIndex,
                transitionSpec = { fadeSpec },
                label = "buttonText"
            ) { index ->
                Text(
                    text = welcomeTranslations[index].buttonText,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Help text
        Text(
            text = "Available in 6 languages | HIPAA Compliant | Secure",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Demo mode entry — for presentations and partner demos
        OutlinedButton(
            onClick = {
                viewModel.startSession()
                onDemoMode()
            },
            modifier = Modifier
                .width(200.dp)
                .height(48.dp)
        ) {
            Text(
                text = "Demo Mode",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
