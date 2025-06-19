plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("dev.rubentxu.hodei.packages.app.Application")
}

dependencies {
    implementation(project(":backend:application"))
    implementation(project(":backend:domain"))
    implementation(project(":backend:infrastructure"))
    
    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.ktor.server.requestValidation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.hikariCP)
    
    // Testing
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlin.test.junit5)

    // Koin for dependency injection
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    testImplementation(libs.koin.test) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
    }
    testImplementation(libs.koin.test.junit5) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
    }


    // MockK for mocking
    testImplementation(libs.mockk)

    // Kotlinx Coroutines Test
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform() // Para que Kotest funcione correctamente
    testLogging {
        events("passed", "skipped", "failed") // Muestra eventos de test en la consola
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL // Muestra stack traces completos
        showStandardStreams = true // Muestra stdout/stderr de los tests
    }
}
