package com.medpull.kiosk.ui.screens.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.ui.components.BackButton
import com.medpull.kiosk.utils.LanguageOption

/**
 * Language selection screen.
 *
 * Self-explanatory 2x4 grid of large cards, each showing one language in its
 * own script (e.g. `English`, `中文`, `العربية`). No title, no subtitle, no
 * confirm button — tapping a card immediately persists the language and
 * navigates to the next step.
 *
 * Layout uses nested Row/Column with weight so the 8 cards divide the screen
 * evenly regardless of device size, on tablet landscape or otherwise.
 */
@Composable
fun LanguageSelectionScreen(
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
    onLanguageSelected: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.availableLanguages.size < 8) {
        // Languages not loaded yet (or unexpectedly short); render an empty
        // surface so the screen still fills cleanly during the brief load.
        Box(modifier = Modifier.fillMaxSize()) {
            BackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        return
    }

    // Render exactly 4 rows × 2 cards. Pulling slices in order matches the
    // canonical picker order defined in Constants.Languages.ALL.
    val rows = uiState.availableLanguages.chunked(2)

    Column(modifier = Modifier.fillMaxSize()) {
        // Back arrow sits in its own top bar above the grid (no overlap).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            rows.forEach { rowLanguages ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowLanguages.forEach { language ->
                        LanguageCard(
                            language = language,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                viewModel.selectLanguage(language.code)
                                viewModel.confirmLanguage(onLanguageSelected)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single card showing one language's name in its own script, centered.
 */
@Composable
private fun LanguageCard(
    language: LanguageOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
