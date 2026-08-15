package com.nova.runtime.conversation.di

import com.nova.runtime.conversation.ConversationService
import com.nova.runtime.conversation.ConversationServiceImpl
import com.nova.runtime.conversation.dialogue.DialogueManager
import com.nova.runtime.conversation.events.ConversationEventPublisher
import com.nova.runtime.conversation.input.UnifiedInputProcessor
import com.nova.runtime.conversation.observation.ObservationGenerator
import com.nova.runtime.conversation.response.ConversationResponseGenerator
import com.nova.runtime.conversation.response.ResponsePipeline
import com.nova.runtime.conversation.response.StubConversationResponseGenerator
import com.nova.runtime.conversation.session.SessionManager
import com.nova.runtime.conversation.speech.SpeechRecognizer
import com.nova.runtime.conversation.speech.StubSpeechRecognizer
import com.nova.runtime.conversation.speech.StubTextToSpeech
import com.nova.runtime.conversation.speech.TextToSpeech
import org.koin.dsl.module

/** Koin DI wiring for Conversation Service per MSP §3. */
val conversationModule = module {
    single<SpeechRecognizer> { StubSpeechRecognizer() }
    single<TextToSpeech> { StubTextToSpeech() }
    single<ConversationResponseGenerator> { StubConversationResponseGenerator() }
    single { ResponsePipeline(get()) }
    single { DialogueManager() }
    single { ObservationGenerator(get()) }
    single { UnifiedInputProcessor(get()) }
    single { ConversationEventPublisher(get()) }
    single { SessionManager(get(), get(), get()) }
    single<ConversationService> {
        ConversationServiceImpl(
            sessionManager = get(),
            dialogueManager = get(),
            inputProcessor = get(),
            observationGenerator = get(),
            responsePipeline = get(),
            eventPublisher = get(),
            textToSpeech = get(),
            traceContextHolder = get(),
            logger = get(),
        )
    }
}
