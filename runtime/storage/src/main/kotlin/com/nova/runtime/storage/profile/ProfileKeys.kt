package com.nova.runtime.storage.profile

/** PreferenceEntity keys for the onboarding personality profile (Phase 3C). */
object ProfileKeys {
    const val WORK_HOURS_START = "profile.work_hours_start"
    const val WORK_HOURS_END = "profile.work_hours_end"
    const val TIMEZONE = "profile.timezone"
    const val PRIORITY_TOPICS = "profile.priority_topics"
    const val DEFAULT_MEETING_MINUTES = "profile.default_meeting_minutes"
    const val REMINDER_LEAD_MINUTES = "profile.reminder_lead_minutes"
    const val KEY_CONTACTS = "profile.key_contacts"
    const val ONBOARDING_COMPLETE = "profile.onboarding_complete"

    val ALL = listOf(
        WORK_HOURS_START,
        WORK_HOURS_END,
        TIMEZONE,
        PRIORITY_TOPICS,
        DEFAULT_MEETING_MINUTES,
        REMINDER_LEAD_MINUTES,
        KEY_CONTACTS,
        ONBOARDING_COMPLETE,
    )
}
