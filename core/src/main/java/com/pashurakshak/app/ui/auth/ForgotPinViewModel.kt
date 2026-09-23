package com.pashurakshak.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AuthRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPinUiState(
    val step: Step = Step.EMAIL,
    val email: String = "",
    val otpCode: String = "",
    val newPin: String = "",
    val newPinConfirm: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val maskedEmail: String? = null,
    val resendCooldown: Int = 0,
    val verifyToken: String? = null,
    val completed: Boolean = false,
) {
    enum class Step { EMAIL, RESET }

    val canSendOtp: Boolean
        get() = email.contains("@") && resendCooldown == 0 && !isLoading
    val canReset: Boolean
        get() = otpCode.length == 6 && newPin.length == 6 && newPin == newPinConfirm && !isLoading
}

class ForgotPinViewModel : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private var cooldownJob: Job? = null

    private val purpose: String =
        if (SessionManager.appRole == SessionManager.Role.VET) "vet_reset_pin" else "farmer_reset_pin"

    private val _uiState = MutableStateFlow(ForgotPinUiState())
    val uiState: StateFlow<ForgotPinUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value.trim(), error = null) }
    fun onOtpChanged(value: String) = _uiState.update {
        it.copy(otpCode = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun onNewPinChanged(value: String) = _uiState.update {
        it.copy(newPin = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun onNewPinConfirmChanged(value: String) = _uiState.update {
        it.copy(newPinConfirm = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun sendOtp() {
        val state = _uiState.value
        if (!state.canSendOtp) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auth.sendOtp(state.email, purpose).fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            step = ForgotPinUiState.Step.RESET,
                            maskedEmail = info.maskedEmail,
                            resendCooldown = info.cooldownSeconds,
                        )
                    }
                    startCooldown(info.cooldownSeconds)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Could not send the code.")
                    }
                },
            )
        }
    }

    /** Verifies the code, then resets the PIN (both steps → session on success). */
    fun resetPin() {
        val state = _uiState.value
        if (!state.canReset) {
            _uiState.update { it.copy(error = "PINs must match (6 digits). Enter the code too.") }
            return
        }
        val verifyToken = state.verifyToken
        if (verifyToken != null) {
            submitReset(verifyToken)
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auth.verifyOtp(state.email, purpose, state.otpCode).fold(
                onSuccess = { token ->
                    _uiState.update { it.copy(verifyToken = token) }
                    submitReset(token)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Invalid code.")
                    }
                },
            )
        }
    }

    private fun submitReset(verifyToken: String) {
        val state = _uiState.value
        viewModelScope.launch {
            val result = when (SessionManager.appRole) {
                SessionManager.Role.FARMER -> auth.resetFarmerPin(verifyToken, state.newPin)
                SessionManager.Role.VET -> auth.resetVetPin(verifyToken, state.newPin)
            }
            when (result) {
                is AuthRepository.AuthResult.Success -> {
                    auth.establishSession(
                        token = result.token,
                        user = result.user,
                        profile = result.profile,
                        role = SessionManager.appRole,
                    )
                    _uiState.update { it.copy(isLoading = false, completed = true) }
                }

                is AuthRepository.AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _uiState.update { it.copy(resendCooldown = remaining) }
            }
        }
    }
}
