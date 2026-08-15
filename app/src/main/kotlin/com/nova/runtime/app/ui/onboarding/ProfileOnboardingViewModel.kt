package com.nova.runtime.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.runtime.storage.profile.ProfilePreferencesStore
import com.nova.runtime.storage.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

data class ProfileOnboardingUiState(
    val workHoursStart: String = "09:00",
    val workHoursEnd: String = "17:00",
    val selectedTopics: Set<String> = setOf("meetings", "deadlines"),
    val defaultMeetingMinutes: Int = 30,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class ProfileOnboardingViewModel(
    private val profileStore: ProfilePreferencesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileOnboardingUiState())
    val uiState: StateFlow<ProfileOnboardingUiState> = _uiState.asStateFlow()

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    val availableTopics = listOf("meetings", "deadlines", "family", "finance", "health", "travel")

    val meetingDurationOptions = listOf(15, 30, 45, 60)

    init {
        viewModelScope.launch {
            _showOnboarding.value = !profileStore.isOnboardingComplete()
            val existing = profileStore.loadProfile()
            if (existing.onboardingComplete) {
                _uiState.value = ProfileOnboardingUiState(
                    workHoursStart = existing.workHoursStart,
                    workHoursEnd = existing.workHoursEnd,
                    selectedTopics = existing.priorityTopics.toSet(),
                    defaultMeetingMinutes = existing.defaultMeetingMinutes,
                )
            }
        }
    }

    fun updateWorkHoursStart(value: String) {
        _uiState.value = _uiState.value.copy(workHoursStart = value)
    }

    fun updateWorkHoursEnd(value: String) {
        _uiState.value = _uiState.value.copy(workHoursEnd = value)
    }

    fun toggleTopic(topic: String) {
        val current = _uiState.value.selectedTopics
        _uiState.value = _uiState.value.copy(
            selectedTopics = if (topic in current) current - topic else current + topic,
        )
    }

    fun updateMeetingMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(defaultMeetingMinutes = minutes)
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            runCatching {
                val state = _uiState.value
                profileStore.saveProfile(
                    UserProfile(
                        workHoursStart = state.workHoursStart,
                        workHoursEnd = state.workHoursEnd,
                        timezone = TimeZone.getDefault().id,
                        priorityTopics = state.selectedTopics.sorted(),
                        defaultMeetingMinutes = state.defaultMeetingMinutes,
                        onboardingComplete = true,
                    ),
                )
            }.onSuccess {
                _showOnboarding.value = false
                _uiState.value = _uiState.value.copy(isSaving = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Could not save profile",
                )
            }
        }
    }

    fun dismissForNow() {
        viewModelScope.launch {
            profileStore.saveProfile(
                UserProfile(
                    workHoursStart = _uiState.value.workHoursStart,
                    workHoursEnd = _uiState.value.workHoursEnd,
                    timezone = TimeZone.getDefault().id,
                    priorityTopics = _uiState.value.selectedTopics.sorted(),
                    defaultMeetingMinutes = _uiState.value.defaultMeetingMinutes,
                    onboardingComplete = true,
                ),
            )
            _showOnboarding.value = false
        }
    }
}
