package com.nova.runtime.storage.profile

import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.repository.PreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePreferencesStoreTest {

    @Test
    fun saveAndLoadProfile_roundTrips() = runTest {
        val repo = InMemoryPreferenceRepository()
        val store = ProfilePreferencesStore(repo)

        store.saveProfile(
            UserProfile(
                workHoursStart = "08:30",
                workHoursEnd = "18:00",
                timezone = "Asia/Kolkata",
                priorityTopics = listOf("meetings", "finance"),
                defaultMeetingMinutes = 45,
                onboardingComplete = true,
            ),
        )

        val loaded = store.loadProfile()
        assertEquals("08:30", loaded.workHoursStart)
        assertEquals("18:00", loaded.workHoursEnd)
        assertEquals(listOf("meetings", "finance"), loaded.priorityTopics)
        assertEquals(45, loaded.defaultMeetingMinutes)
        assertTrue(store.isOnboardingComplete())
    }

    private class InMemoryPreferenceRepository : PreferenceRepository {
        private val data = mutableMapOf<String, PreferenceEntity>()

        override suspend fun insert(preference: PreferenceEntity) {
            data[preference.key] = preference
        }

        override suspend fun update(preference: PreferenceEntity) {
            data[preference.key] = preference
        }

        override suspend fun delete(key: String) {
            data.remove(key)
        }

        override suspend fun getByKey(key: String): PreferenceEntity? = data[key]

        override fun observeByKey(key: String): Flow<PreferenceEntity?> = flowOf(data[key])
    }
}
