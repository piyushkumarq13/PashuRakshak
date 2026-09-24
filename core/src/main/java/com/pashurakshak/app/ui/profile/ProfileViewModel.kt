package com.pashurakshak.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.data.FarmerRepository
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val editing: Boolean = false,
    val loggedOut: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val village: String = "",
    val pincode: String = "",
    val animalCountText: String = "",
    val language: String = "hi",
    val notice: String? = null,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && village.isNotBlank() && pincode.isNotBlank()
}

class ProfileViewModel(
    private val farmerRepository: FarmerRepository = ServiceLocator.farmerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var baseline = ProfileUiState()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.pullAll() }

            var name = SessionManager.name.orEmpty()
            var email = SessionManager.email.orEmpty()
            var village = SessionManager.village.orEmpty()
            var pincode = SessionManager.pincode.orEmpty()
            var animalCount = SessionManager.animalCount
            var language = SessionManager.preferredLanguage ?: "hi"

            when (val userResult = farmerRepository.getProfile()) {
                is FarmerRepository.Result.Success -> {
                    userResult.profile?.let { user ->
                        if (user.name.isNotBlank()) name = user.name
                        if (user.email.isNotBlank()) email = user.email
                        if (user.preferredLanguage.isNotBlank()) language = user.preferredLanguage
                    }
                }
                is FarmerRepository.Result.Failure -> {
                    // Keep local SessionManager values if the network fails.
                }
            }

            when (val profileResult = farmerRepository.getFarmerProfile()) {
                is FarmerRepository.ProfileResult.Success -> {
                    profileResult.profile?.let { profile ->
                        if (profile.village.isNotBlank()) village = profile.village
                        if (profile.pincode.isNotBlank()) pincode = profile.pincode
                        animalCount = profile.animalCount
                    }
                }
                is FarmerRepository.ProfileResult.Failure -> Unit
            }

            val loaded = ProfileUiState(
                isLoading = false,
                name = name,
                email = email,
                phone = SessionManager.phone.orEmpty(),
                village = village,
                pincode = pincode,
                animalCountText = animalCount.toString(),
                language = language,
            )
            baseline = loaded
            _uiState.update { loaded }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(editing = true) }
    }

    fun cancelEditing() {
        _uiState.update { baseline.copy(editing = false) }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onVillageChanged(value: String) = _uiState.update { it.copy(village = value) }
    fun onPincodeChanged(value: String) = _uiState.update { it.copy(pincode = value) }
    fun onAnimalCountChanged(value: String) =
        _uiState.update { it.copy(animalCountText = value.filter(Char::isDigit)) }

    fun onLanguageChanged(value: String) = _uiState.update { it.copy(language = value) }

    fun save() {
        val current = _uiState.value
        if (!current.canSave) return
        val animalCount = current.animalCountText.toIntOrNull() ?: 0

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val userUpdate = farmerRepository.updateUserProfile(
                name = current.name.trim(),
                email = current.email.trim(),
                preferredLanguage = current.language,
            )
            if (userUpdate is FarmerRepository.Result.Failure) {
                _uiState.update {
                    it.copy(isSaving = false, error = userUpdate.message)
                }
                return@launch
            }

            val profileUpdate = farmerRepository.updateFarmerProfile(
                animalCount = animalCount,
                village = current.village.trim(),
                pincode = current.pincode.trim(),
            )
            if (profileUpdate is FarmerRepository.ProfileResult.Failure) {
                _uiState.update {
                    it.copy(isSaving = false, error = profileUpdate.message)
                }
                return@launch
            }

            SessionManager.setFarmerProfile(
                name = current.name.trim(),
                email = current.email.trim(),
                preferredLanguage = current.language,
                animalCount = animalCount,
                village = current.village.trim(),
                pincode = current.pincode.trim(),
            )

            val saved = current.copy(
                isSaving = false,
                editing = false,
                animalCountText = animalCount.toString(),
                notice = "Profile updated",
            )
            baseline = saved.copy(notice = null)
            _uiState.update { saved }
        }
    }

    fun logout() {
        SessionManager.logout()
        _uiState.update { it.copy(loggedOut = true) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
