package com.nova.runtime.storage.profile

import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.repository.PreferenceRepository

/** Reads and writes the onboarding personality profile via PreferenceEntity. */
class ProfilePreferencesStore(
    private val preferenceRepository: PreferenceRepository,
) {
    suspend fun isOnboardingComplete(): Boolean =
        preferenceRepository.getByKey(ProfileKeys.ONBOARDING_COMPLETE)?.value
            ?.equals("true", ignoreCase = true) == true

    suspend fun loadProfile(): UserProfile {
        return UserProfile(
            workHoursStart = prefValue(ProfileKeys.WORK_HOURS_START) ?: "09:00",
            workHoursEnd = prefValue(ProfileKeys.WORK_HOURS_END) ?: "17:00",
            timezone = prefValue(ProfileKeys.TIMEZONE).orEmpty(),
            priorityTopics = prefValue(ProfileKeys.PRIORITY_TOPICS)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            defaultMeetingMinutes = prefValue(ProfileKeys.DEFAULT_MEETING_MINUTES)?.toIntOrNull() ?: 30,
            reminderLeadMinutes = prefValue(ProfileKeys.REMINDER_LEAD_MINUTES)?.toIntOrNull() ?: 15,
            keyContacts = prefValue(ProfileKeys.KEY_CONTACTS)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            onboardingComplete = prefValue(ProfileKeys.ONBOARDING_COMPLETE)
                ?.equals("true", ignoreCase = true) == true,
        )
    }

    private suspend fun prefValue(key: String): String? =
        preferenceRepository.getByKey(key)?.value

    suspend fun saveProfile(profile: UserProfile) {
        val now = System.currentTimeMillis()
        profile.toPreferenceMap().forEach { (key, value) ->
            preferenceRepository.insert(
                PreferenceEntity(
                    key = key,
                    value = value,
                    confidence = 1f,
                    updatedAt = now,
                ),
            )
        }
    }
}
