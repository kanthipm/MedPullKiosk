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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import kotlinx.coroutines.delay

private data class WelcomeTranslation(val buttonText: String, val welcomeMessage: String)

// Cycles through the 8 supported languages. Order matches the picker grid:
// English, Español, 中文, Français, 日本語, Português, العربية, Русский.
private val welcomeTranslations = listOf(
    WelcomeTranslation("Get Started", "Welcome! Tap below to get started."),
    WelcomeTranslation("Comenzar", "¡Bienvenido! Toque abajo para comenzar."),
    WelcomeTranslation("开始", "欢迎！点击下方开始填写您的医疗表格。"),
    WelcomeTranslation("Commencer", "Bienvenue ! Appuyez ci-dessous pour commencer."),
    WelcomeTranslation("はじめる", "ようこそ！下をタップして始めてください。"),
    WelcomeTranslation("Começar", "Bem-vindo! Toque abaixo para começar."),
    WelcomeTranslation("ابدأ", "!مرحبًا! انقر أدناه للبدء"),
    WelcomeTranslation("Начать", "Добро пожаловать! Нажмите ниже, чтобы начать."),
)

/**
 * Welcome screen - entry point of the app
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = hiltViewModel(),
    onContinue: () -> Unit,
    onStaffView: () -> Unit = {}
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

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo/branding
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_subtitle),
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
            text = stringResource(R.string.features_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

    } // end Column

    // Staff View — bottom-end corner
    OutlinedButton(
        onClick = onStaffView,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AdminPanelSettings,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = stringResource(R.string.staff_view),
            style = MaterialTheme.typography.labelLarge
        )
    }

    } // end Box
}
