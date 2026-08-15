package com.nova.runtime.storage.profile

/** User personality profile loaded from PreferenceEntity rows. */
data class UserProfile(
    val workHoursStart: String = "09:00",
    val workHoursEnd: String = "17:00",
    val timezone: String = "",
    val priorityTopics: List<String> = emptyList(),
    val defaultMeetingMinutes: Int = 30,
    val reminderLeadMinutes: Int = 15,
    val keyContacts: List<String> = emptyList(),
    val onboardingComplete: Boolean = false,
) {
    fun toPreferenceMap(): Map<String, String> = buildMap {
        put(ProfileKeys.WORK_HOURS_START, workHoursStart)
        put(ProfileKeys.WORK_HOURS_END, workHoursEnd)
        put(ProfileKeys.TIMEZONE, timezone)
        put(ProfileKeys.PRIORITY_TOPICS, priorityTopics.joinToString(","))
        put(ProfileKeys.DEFAULT_MEETING_MINUTES, defaultMeetingMinutes.toString())
        put(ProfileKeys.REMINDER_LEAD_MINUTES, reminderLeadMinutes.toString())
        put(ProfileKeys.KEY_CONTACTS, keyContacts.joinToString(","))
        put(ProfileKeys.ONBOARDING_COMPLETE, onboardingComplete.toString())
    }
}
