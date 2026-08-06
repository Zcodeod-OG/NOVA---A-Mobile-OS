package com.nova.runtime.conversation.di

import com.nova.runtime.conversation.session.InMemorySessionPersistence
import com.nova.runtime.conversation.session.SessionPersistence
import org.koin.dsl.module

/** In-memory session persistence for JVM tests and non-Android hosts. */
val inMemorySessionPersistenceModule = module {
    single<SessionPersistence> { InMemorySessionPersistence() }
}
