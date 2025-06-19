package dev.rubentxu.hodei.packages.app.di

import dev.rubentxu.hodei.packages.application.events.handlers.AuthEventHandler
import dev.rubentxu.hodei.packages.domain.ports.security.UserRepository
import dev.rubentxu.hodei.packages.infrastructure.persistence.DatabaseFactory
import dev.rubentxu.hodei.packages.infrastructure.repository.UserRepositoryImpl
import org.koin.dsl.module

val persistenceModule = module {
    single { DatabaseFactory.init() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single { AuthEventHandler(get()) }
}
