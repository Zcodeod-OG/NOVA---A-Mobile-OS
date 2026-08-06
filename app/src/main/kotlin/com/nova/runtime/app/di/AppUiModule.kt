package com.nova.runtime.app.di

import com.nova.runtime.app.conversation.OrchestratorConversationResponseGenerator
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.conversation.response.ConversationResponseGenerator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appUiModule = module {
    single<ConversationResponseGenerator>(override = true) { OrchestratorConversationResponseGenerator(get()) }
    viewModel { NovaOsViewModel(get(), get()) }
}
