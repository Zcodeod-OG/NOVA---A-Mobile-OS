package com.nova.runtime.storage.profile

import com.nova.runtime.understanding.scoring.UserProfileContext

fun UserProfile.toScoringContext(): UserProfileContext =
    UserProfileContext(
        priorityTopics = priorityTopics.map { it.lowercase() }.toSet(),
        keyContacts = keyContacts.map { it.lowercase() }.toSet(),
        defaultMeetingMinutes = defaultMeetingMinutes,
        workHoursStart = workHoursStart,
        workHoursEnd = workHoursEnd,
    )
