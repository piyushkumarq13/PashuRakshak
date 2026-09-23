package com.pashurakshak.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.FarmerRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {

    private val farmerRepository = ServiceLocator.farmerRepository

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun onEmailChange(email: String) { _uiState.value = _uiState.value.copy(email = email) }
    fun onAnimalCountChange(count: String) { _uiState.value = _uiState.value.copy(animalCountText = count) }
    fun onVillageChange(village: String) { _uiState.value = _uiState.value.copy(village = village) }
    fun onPincodeChange(pincode: String) { _uiState.value = _uiState.value.copy(pincode = pincode) }
    fun onLanguageChange(language: String) { _uiState.value = _uiState.value.copy(language = language) }

    fun submit() {
        val state = _uiState.value
        if (state.name.isBlank() || state.email.isBlank() || state.village.isBlank() || state.pincode.isBlank()) return
        val animalCount = state.animalCountText.toIntOrNull() ?: return
        if (animalCount <= 0) return
        if (state.language.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val uid = SessionManager.uid ?: ""
            val phone = SessionManager.phone ?: ""

            val createUserResult = farmerRepository.createUser(
                name = state.name,
                email = state.email,
                phone = phone,
                preferredLanguage = state.language,
            )
            if (createUserResult is FarmerRepository.Result.Failure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = createUserResult.message)
                return@launch
            }

            val profileResult = farmerRepository.createFarmerProfile(
                animalCount = animalCount,
                village = state.village,
                pincode = state.pincode,
            )
            if (profileResult is FarmerRepository.Result.Failure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = profileResult.message)
                return@launch
            }

            SessionManager.setFarmerProfile(
                name = state.name,
                email = state.email,
                preferredLanguage = state.language,
                animalCount = animalCount,
                village = state.village,
                pincode = state.pincode,
            )
            _uiState.value = _uiState.value.copy(isLoading = false, isSubmitted = true)
        }
    }

    fun resetSubmitted() {
        _uiState.value = _uiState.value.copy(isSubmitted = false)
    }
}

data class OnboardingUiState(
    val name: String = "",
    val email: String = "",
    val animalCountText: String = "",
    val village: String = "",
    val pincode: String = "",
    val language: String = "hi",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSubmitted: Boolean = false,
)
