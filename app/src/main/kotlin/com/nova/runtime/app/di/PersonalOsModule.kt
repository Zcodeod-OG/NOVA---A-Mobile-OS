package com.nova.runtime.app.di

import com.nova.runtime.app.calendar.AppCalendarIntentSupport
import com.nova.runtime.orchestrator.calendar.CalendarIntentSupport
import org.koin.dsl.module

/** App-layer overrides for personal-assistant calendar and memory wiring. */
val personalOsModule = module {
    single<CalendarIntentSupport> { AppCalendarIntentSupport(get(), get()) }
}
