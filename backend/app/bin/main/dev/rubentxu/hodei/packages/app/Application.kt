package dev.rubentxu.hodei.packages.app

import dev.rubentxu.hodei.packages.app.di.persistenceModule
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import dev.rubentxu.hodei.packages.app.plugins.configureRouting
import dev.rubentxu.hodei.packages.app.plugins.configureSerialization

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        modules(persistenceModule)
    }
    
    configureRouting()
}
