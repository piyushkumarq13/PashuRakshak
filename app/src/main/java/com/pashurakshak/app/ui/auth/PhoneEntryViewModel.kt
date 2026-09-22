package com.pashurakshak.app.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.PhoneAuthCredential
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhoneEntryUiState(
    val digits: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Set once SMS sending started → navigate to the OTP screen. */
    val verificationId: String? = null,
    /** Set when instant/auto verification signed the user in → skip OTP screen. */
    val autoSignedIn: Boolean = false,
) {
    val e164: String?
        get() = when {
            digits.startsWith("+") && digits.length >= 10 -> digits
            digits.length == 10 -> "+91$digits"
            else -> null
        }

    val canSubmit: Boolean
        get() = e164 != null && !isLoading
}

class PhoneEntryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneEntryUiState())
    val uiState: StateFlow<PhoneEntryUiState> = _uiState.asStateFlow()

    fun onDigitsChanged(value: String) {
        _uiState.update {
            it.copy(
                digits = value.filter { ch -> ch.isDigit() || ch == '+' }.take(16),
                error = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun sendCode(activity: Activity) {
        val state = _uiState.value
        val e164 = state.e164 ?: run {
            _uiState.update {
                it.copy(error = "Enter a valid phone number (10 digits, or with country code)")
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, verificationId = null) }

        ServiceLocator.authRepository.sendVerificationCode(
            activity = activity,
            e164PhoneNumber = e164,
            onCodeSent = { verificationId ->
                _uiState.update { it.copy(isLoading = false, verificationId = verificationId) }
            },
            onVerificationCompleted = { credential ->
                signInInstant(credential, e164)
            },
            onError = { message ->
                _uiState.update { it.copy(isLoading = false, error = message) }
            },
        )
    }

    /** Instant verification (no SMS shown) — sign in and jump straight to role selection. */
    private fun signInInstant(credential: PhoneAuthCredential, e164: String) {
        ServiceLocator.authRepository.signInWithCredential(
            credential = credential,
            onSuccess = { uid ->
                SessionManager.onOtpVerified(uid, e164)
                _uiState.update { it.copy(isLoading = false, autoSignedIn = true) }
            },
            onError = { message ->
                _uiState.update { it.copy(isLoading = false, error = message) }
            },
        )
    }

    fun consumedVerificationId() {
        _uiState.update { it.copy(verificationId = null) }
    }

    fun consumedAutoSignIn() {
        _uiState.update { it.copy(autoSignedIn = false) }
    }
}
