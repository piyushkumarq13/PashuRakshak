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

data class RegisterUiState(
    val step: Step = Step.CONTACT,
    val phone: String = "",
    val email: String = "",
    val otpCode: String = "",
    val name: String = "",
    val pin: String = "",
    val pinConfirm: String = "",
    val language: String = "hi",
    val animalCountText: String = "",
    val village: String = "",
    val pincode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val maskedEmail: String? = null,
    val resendCooldown: Int = 0,
    val verifyToken: String? = null,
    val registered: Boolean = false,
) {
    enum class Step { CONTACT, OTP, DETAILS }

    val phoneDigits: String get() = phone.filter { it.isDigit() }.removePrefix("0").takeLast(10)
    val canSendOtp: Boolean
        get() = phoneDigits.length == 10 && email.contains("@") && resendCooldown == 0 && !isLoading
    val canVerifyOtp: Boolean get() = otpCode.length == 6 && !isLoading
    val canRegister: Boolean
        get() = name.isNotBlank() &&
            pin.length == 6 &&
            pin == pinConfirm &&
            village.isNotBlank() &&
            pincode.length == 6 &&
            !isLoading
}

class RegisterViewModel : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private var cooldownJob: Job? = null

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(value: String) = _uiState.update {
        it.copy(phone = value.filter { ch -> ch.isDigit() || ch == '+' }.take(16), error = null)
    }

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value.trim(), error = null) }
    fun onOtpChanged(value: String) = _uiState.update {
        it.copy(otpCode = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun onPinChanged(value: String) = _uiState.update {
        it.copy(pin = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun onPinConfirmChanged(value: String) = _uiState.update {
        it.copy(pinConfirm = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun onLanguageChanged(value: String) = _uiState.update { it.copy(language = value) }
    fun onAnimalCountChanged(value: String) = _uiState.update {
        it.copy(animalCountText = value.filter { ch -> ch.isDigit() }.take(4), error = null)
    }

    fun onVillageChanged(value: String) = _uiState.update { it.copy(village = value, error = null) }
    fun onPincodeChanged(value: String) = _uiState.update {
        it.copy(pincode = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun sendOtp() {
        val state = _uiState.value
        if (!state.canSendOtp) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auth.sendOtp(state.email, PURPOSE, phone = state.phoneDigits).fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            step = RegisterUiState.Step.OTP,
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

    fun verifyOtp() {
        val state = _uiState.value
        if (!state.canVerifyOtp) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auth.verifyOtp(state.email, PURPOSE, state.otpCode).fold(
                onSuccess = { verifyToken ->
                    _uiState.update {
                        it.copy(isLoading = false, step = RegisterUiState.Step.DETAILS, verifyToken = verifyToken)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Invalid code.")
                    }
                },
            )
        }
    }

    fun register() {
        val state = _uiState.value
        if (!state.canRegister) {
            _uiState.update { it.copy(error = "PINs must match (6 digits). Fill all fields.") }
            return
        }
        val verifyToken = state.verifyToken
        if (verifyToken == null) {
            _uiState.update { it.copy(error = "Verification expired. Start again.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = auth.registerFarmer(
                verifyToken = verifyToken,
                phone = state.phoneDigits,
                pin = state.pin,
                name = state.name.trim(),
                preferredLanguage = state.language,
                animalCount = state.animalCountText.toIntOrNull(),
                village = state.village.trim(),
                pincode = state.pincode,
            )
            when (result) {
                is AuthRepository.AuthResult.Success -> {
                    auth.establishSession(
                        token = result.token,
                        user = result.user,
                        profile = result.profile,
                        role = SessionManager.Role.FARMER,
                    )
                    _uiState.update { it.copy(isLoading = false, registered = true) }
                }

                is AuthRepository.AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun backToContact() {
        cooldownJob?.cancel()
        _uiState.update {
            it.copy(step = RegisterUiState.Step.CONTACT, otpCode = "", error = null, resendCooldown = 0)
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

    private companion object {
        const val PURPOSE = "farmer_register"
    }
}
