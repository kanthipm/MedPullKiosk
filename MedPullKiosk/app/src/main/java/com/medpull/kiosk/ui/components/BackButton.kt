package com.medpull.kiosk.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medpull.kiosk.R

/**
 * Standard kiosk back button used on every screen.
 *
 * Navigates to the previous screen in the flow (wired per-screen in NavGraph so
 * it follows the forward flow in reverse). Uses an auto-mirrored arrow so it
 * points the correct way in right-to-left languages (e.g. Arabic), and the
 * "Back" content description is localized.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
    }
}
