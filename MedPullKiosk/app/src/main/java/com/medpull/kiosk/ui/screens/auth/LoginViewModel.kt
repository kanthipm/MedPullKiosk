package com.medpull.kiosk.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.R
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.AuthResult
import com.medpull.kiosk.utils.AppStrings
import com.medpull.kiosk.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Login screen
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val appStrings: AppStrings
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Update email field
     */
    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            error = null
        )
    }

    /**
     * Update password field
     */
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            error = null
        )
    }

    /**
     * Perform login
     */
    fun login(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        // Validate input
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = appStrings.get(R.string.err_enter_email_password)
            )
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(
                error = appStrings.get(R.string.err_invalid_email)
            )
            return
        }

        // Start login
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                when (val result = authRepository.signIn(email, password)) {
                    is AuthResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null
                        )
                        // Start session
                        sessionManager.startSession()
                        onSuccess()
                    }
                    is AuthResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                    is AuthResult.RequiresConfirmation -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            requiresVerification = true
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: appStrings.get(R.string.err_login_failed)
                )
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for login screen
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val requiresVerification: Boolean = false
)
