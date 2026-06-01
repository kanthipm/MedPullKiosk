package com.medpull.kiosk.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Place this at the top of a [androidx.compose.ui.window.Dialog]'s content.
 *
 * A Compose `Dialog` lives in its own window, which does NOT inherit the
 * activity's `android:windowSoftInputMode="adjustResize"`. By default the dialog
 * window pans (or does nothing) when the on-screen keyboard appears, which leaves
 * centered dialogs and bottom-pinned inputs hidden behind the keyboard.
 *
 * Forcing the dialog window to ADJUST_RESIZE makes it shrink to the space above
 * the keyboard — exactly like the rest of the app — so centered dialogs re-center
 * above the keyboard and bottom-pinned inputs stay visible while typing.
 */
@Composable
fun DialogResizeForKeyboard() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        // ADJUST_RESIZE is the documented way to control a dialog window's IME
        // behavior and works on every supported API level; the API-30 deprecation
        // only applies to the activity's main window, not dialog windows.
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
