package com.pashurakshak.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.AuthRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val phone: String = "",
    val credential: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Set when sign-in + local session are done → navigate to home/onboarding. */
    val loggedIn: Boolean = false,
) {
    val phoneDigits: String get() = phone.filter { it.isDigit() }.removePrefix("0").takeLast(10)
    val canSubmit: Boolean get() = phoneDigits.length == 10 && credential.isNotBlank() && !isLoading
    val isVetApp: Boolean get() = SessionManager.appRole == SessionManager.Role.VET
}

class LoginViewModel : ViewModel() {

    private val auth = ServiceLocator.authRepository

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(value: String) {
        _uiState.update {
            it.copy(phone = value.filter { ch -> ch.isDigit() || ch == '+' }.take(16), error = null)
        }
    }

    fun onCredentialChanged(value: String) {
        _uiState.update { it.copy(credential = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = when (SessionManager.appRole) {
                SessionManager.Role.FARMER -> auth.loginFarmer(state.phoneDigits, state.credential.trim())
                SessionManager.Role.VET -> auth.loginVet(state.phoneDigits, state.credential.trim())
            }
            when (result) {
                is AuthRepository.AuthResult.Success -> {
                    val role = SessionManager.appRole
                    auth.establishSession(
                        token = result.token,
                        user = result.user,
                        profile = result.profile,
                        role = role,
                    )
                    _uiState.update { it.copy(isLoading = false, loggedIn = true) }
                }

                is AuthRepository.AuthResult.Failure -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun consumedLoggedIn() {
        _uiState.update { it.copy(loggedIn = false) }
    }
}
