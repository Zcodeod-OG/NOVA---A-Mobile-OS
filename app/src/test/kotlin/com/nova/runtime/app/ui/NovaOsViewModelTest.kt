package com.nova.runtime.app.ui

import com.nova.runtime.app.ui.components.ActivityItem
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NovaOsViewModelTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var kernel: RuntimeKernel
    private lateinit var viewModel: NovaOsViewModel

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        kernel = RuntimeKernel.create()
        kernel.bootstrap()
        viewModel = NovaOsViewModel(kernel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateSeedsBootLogs() {
        val currentActivities = viewModel.activities.value
        assertTrue("Activities should contain initial boot logs", currentActivities.isNotEmpty())
        assertTrue(currentActivities.any { it.source == "KERNEL" })
        assertTrue(currentActivities.any { it.source == "MEMORY" })
        assertTrue(currentActivities.any { it.source == "INFERENCE" })
    }

    @Test
    fun testSubmitCommandPublishesCognitiveEvents() = runTest(testDispatcher) {
        val initialSize = viewModel.activities.value.size
        val prompt = "Open Camera and analyze scene"

        viewModel.submitCommand(prompt)
        
        // Ensure all events published in viewModelScope are processed
        advanceUntilIdle()

        val updatedActivities = viewModel.activities.value
        assertTrue("Activities size should increase after submitting command", updatedActivities.size > initialSize)

        // Verify cognitive pipeline events published across modules
        assertTrue(updatedActivities.any { it.source == RuntimeModule.CONVERSATION.name && it.message.contains(prompt) })
        assertTrue(updatedActivities.any { it.source == RuntimeModule.PLANNER.name })
        assertTrue(updatedActivities.any { it.source == RuntimeModule.REASONING.name })
        assertTrue(updatedActivities.any { it.source == RuntimeModule.INFERENCE.name })
        assertTrue(updatedActivities.any { it.source == RuntimeModule.EXECUTION.name })
    }
}
