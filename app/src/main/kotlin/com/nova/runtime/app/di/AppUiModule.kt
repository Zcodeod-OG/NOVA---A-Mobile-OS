package com.nova.runtime.app.di

import android.util.Log
import com.nova.runtime.app.conversation.OrchestratorConversationResponseGenerator
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.conversation.response.ConversationResponseGenerator
import com.nova.runtime.kernel.config.ConfigurationManager
import com.nova.runtime.kernel.config.KernelConfigKeys
import com.nova.runtime.utils.logging.LogLevel
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.utils.logging.StructuredLogger
import com.nova.runtime.app.ui.onboarding.ProfileOnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appUiModule = module {
    // Override in-memory logger with Android logcat sink so device diagnosis is possible.
    single<NovaLogger> {
        val config = get<ConfigurationManager>()
        val levelName = config.get(KernelConfigKeys.LOG_LEVEL) ?: LogLevel.DEBUG.name
        val level = runCatching { LogLevel.valueOf(levelName) }.getOrDefault(LogLevel.DEBUG)
        StructuredLogger(minLevel = level) { entry ->
            val tag = "NOVA/${entry.module}"
            val msg = buildString {
                append(entry.message)
                entry.traceId?.let { append(" trace=").append(it.toString().take(8)) }
                if (entry.metadata.isNotEmpty()) {
                    append(' ')
                    append(entry.metadata.entries.joinToString(" ") { "${it.key}=${it.value}" })
                }
            }
            when (entry.level) {
                LogLevel.ERROR -> Log.e(tag, msg)
                LogLevel.WARN -> Log.w(tag, msg)
                LogLevel.INFO -> Log.i(tag, msg)
                LogLevel.DEBUG, LogLevel.TRACE -> Log.d(tag, msg)
            }
        }
    }
    single<ConversationResponseGenerator> { OrchestratorConversationResponseGenerator(get()) }
    viewModel { NovaOsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ProfileOnboardingViewModel(get()) }
}
