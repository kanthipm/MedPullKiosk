package com.medpull.kiosk.ui.screens.programselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medpull.kiosk.data.models.IntakeProgramType

/**
 * Entry screen: patient picks which program they are here for.
 * Sliding Fee is the default / visually emphasized option.
 */
@Composable
fun ProgramSelectionScreen(
    onProgramSelected: (IntakeProgramType) -> Unit,
    onStaffView: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "What are you here for today?",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Select the option that best describes your visit.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(56.dp))

            // ── Primary option: Sliding Fee ──────────────────────────────────────
            Button(
                onClick = { onProgramSelected(IntakeProgramType.SLIDING_FEE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Apply for Sliding Fee",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Income-based discount on healthcare costs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Secondary option: Medical Intake ─────────────────────────────────
            OutlinedButton(
                onClick = { onProgramSelected(IntakeProgramType.MEDICAL_INTAKE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Complete Medical Intake",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "New patient registration and health history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "HIPAA Compliant | Secure",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }

        // Staff View — bottom-end corner, same as WelcomeScreen
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
                text = "Staff View",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
