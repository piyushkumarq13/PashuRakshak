package com.pashurakshak.app.ui.auth

import androidx.lifecycle.ViewModel
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OtpUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val verified: Boolean = false,
) {
    val canSubmit: Boolean
        get() = code.length == CODE_LENGTH && !isLoading

    companion object {
        const val CODE_LENGTH = 6
    }
}

class OtpViewModel(
    private val verificationId: String,
    private val phoneE164: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    fun onCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                code = value.filter { ch -> ch.isDigit() }.take(OtpUiState.CODE_LENGTH),
                error = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun verify() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        ServiceLocator.authRepository.signInWithOtp(
            verificationId = verificationId,
            code = state.code,
            onSuccess = { uid ->
                SessionManager.onOtpVerified(uid, phoneE164)
                _uiState.update { it.copy(isLoading = false, verified = true) }
            },
            onError = { message ->
                _uiState.update { it.copy(isLoading = false, error = message) }
            },
        )
    }

    fun consumedVerified() {
        _uiState.update { it.copy(verified = false) }
    }
}
