package dev.rubentxu.hodei.packages.app.di

import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthEventHandler
import dev.rubentxu.hodei.packages.artifacts.ports.security.UserRepository
import dev.rubentxu.hodei.packages.infrastructure.persistence.DatabaseFactory
import dev.rubentxu.hodei.packages.infrastructure.repository.UserRepositoryImpl
import org.koin.dsl.module

val persistenceModule = module {
    single { DatabaseFactory.init() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single { AuthEventHandler(get()) }
}
