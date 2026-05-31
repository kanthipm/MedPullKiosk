package com.medpull.kiosk.healthcare.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.medpull.kiosk.healthcare.client.FhirServerConfig
import com.medpull.kiosk.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages SMART on FHIR OAuth2 authentication using AppAuth library.
 * Supports PKCE flow, endpoint discovery, and token refresh.
 */
@Singleton
class SmartAuthManager @Inject constructor(
    private val secureStorageManager: SecureStorageManager
) {
    companion object {
        private const val TAG = "SmartAuthManager"
        private const val KEY_FHIR_ACCESS_TOKEN = "fhir_access_token"
        private const val KEY_FHIR_REFRESH_TOKEN = "fhir_refresh_token"
        private const val KEY_FHIR_TOKEN_EXPIRY = "fhir_token_expiry"
        private const val KEY_FHIR_AUTH_STATE = "fhir_auth_state"
    }

    private var authState: AuthState? = null
    private var authService: AuthorizationService? = null

    /**
     * Discover SMART endpoints from the FHIR server's .well-known configuration.
     */
    suspend fun discoverEndpoints(config: FhirServerConfig): Result<AuthorizationServiceConfiguration> {
        return withContext(Dispatchers.IO) {
            try {
                val issuerUri = Uri.parse(config.baseUrl)
                val serviceConfig = suspendCancellableCoroutine<AuthorizationServiceConfiguration?> { continuation ->
                    AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { cfg, _ ->
                        if (cfg != null) {
                            continuation.resume(cfg)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
                if (serviceConfig != null) {
                    Result.success(serviceConfig)
                } else {
                    Result.failure(Exception("SMART endpoint discovery failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Endpoint discovery failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Build an authorization intent for the SMART on FHIR PKCE flow.
     */
    fun buildAuthIntent(
        context: Context,
        serviceConfig: AuthorizationServiceConfiguration,
        config: FhirServerConfig
    ): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            config.smartClientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.smartRedirectUri)
        )
            .setScopes(config.smartScopes.split(" "))
            .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .build()

        authService?.dispose()
        authService = AuthorizationService(context)
        return authService!!.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle the authorization response and exchange code for tokens.
     */
    suspend fun handleAuthResponse(
        context: Context,
        intent: Intent
    ): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = AuthorizationResponse.fromIntent(intent)
                val exception = AuthorizationException.fromIntent(intent)

                if (response == null) {
                    return@withContext Result.failure<TokenResponse>(
                        exception ?: Exception("Authorization failed")
                    )
                }

                authState = AuthState(response, exception)
                val service = AuthorizationService(context)

                val tokenResponse = suspendCancellableCoroutine<TokenResponse?> { continuation ->
                    service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                        if (tokenResp != null) {
                            authState?.update(tokenResp, tokenEx)
                            saveTokens(tokenResp)
                            continuation.resume(tokenResp)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }

                service.dispose()
                if (tokenResponse != null) {
                    Result.success(tokenResponse)
                } else {
                    Result.failure(Exception("Token exchange failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth response handling failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get the current access token, refreshing if needed.
     */
    fun getAccessToken(): String? {
        val expiry = secureStorageManager.getSecureString(KEY_FHIR_TOKEN_EXPIRY)?.toLongOrNull() ?: 0
        if (System.currentTimeMillis() < expiry) {
            return secureStorageManager.getSecureString(KEY_FHIR_ACCESS_TOKEN)
        }
        return secureStorageManager.getSecureString(KEY_FHIR_ACCESS_TOKEN)
    }

    /**
     * Refresh the access token using the stored refresh token.
     */
    suspend fun refreshToken(context: Context): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val savedState = secureStorageManager.getSecureString(KEY_FHIR_AUTH_STATE)
                val state = if (savedState != null) {
                    AuthState.jsonDeserialize(savedState)
                } else {
                    return@withContext Result.failure<String>(Exception("No auth state saved"))
                }

                if (!state.needsTokenRefresh) {
                    val token = state.accessToken
                    if (token != null) return@withContext Result.success(token)
                }

                val service = AuthorizationService(context)
                val accessToken = suspendCancellableCoroutine<String?> { continuation ->
                    state.performActionWithFreshTokens(service) { token, _, _ ->
                        if (token != null) {
                            secureStorageManager.saveSecureString(KEY_FHIR_ACCESS_TOKEN, token)
                            continuation.resume(token)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
                service.dispose()
                if (accessToken != null) {
                    Result.success(accessToken)
                } else {
                    Result.failure(Exception("Token refresh failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                Result.failure(e)
            }
        }
    }

    fun isAuthenticated(): Boolean {
        return secureStorageManager.getSecureString(KEY_FHIR_ACCESS_TOKEN) != null
    }

    fun clearAuth() {
        secureStorageManager.remove(KEY_FHIR_ACCESS_TOKEN)
        secureStorageManager.remove(KEY_FHIR_REFRESH_TOKEN)
        secureStorageManager.remove(KEY_FHIR_TOKEN_EXPIRY)
        secureStorageManager.remove(KEY_FHIR_AUTH_STATE)
        authState = null
    }

    private fun saveTokens(tokenResponse: TokenResponse) {
        tokenResponse.accessToken?.let {
            secureStorageManager.saveSecureString(KEY_FHIR_ACCESS_TOKEN, it)
        }
        tokenResponse.refreshToken?.let {
            secureStorageManager.saveSecureString(KEY_FHIR_REFRESH_TOKEN, it)
        }
        tokenResponse.accessTokenExpirationTime?.let {
            secureStorageManager.saveSecureString(KEY_FHIR_TOKEN_EXPIRY, it.toString())
        }
        authState?.let {
            secureStorageManager.saveSecureString(KEY_FHIR_AUTH_STATE, it.jsonSerializeString())
        }
    }

    fun dispose() {
        authService?.dispose()
        authService = null
    }
}
