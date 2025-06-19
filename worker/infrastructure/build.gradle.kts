plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // If using kotlinx.serialization for DB DTOs etc.

}

// Configure JUnit Platform for Kotest
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

dependencies {
    implementation(project(":worker:domain"))
    implementation(project(":worker:application"))
    implementation(project(":core:domain"))
    implementation(project(":core:infrastructure"))

    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotest.runner.junit5) // Note: Original was kotest-runner-junit5-jvm:5.8.0, catalog uses 5.8.1
    testImplementation(libs.kotest.assertions.core) // Note: Original was kotest-assertions-core-jvm:5.8.0, catalog uses 5.8.1
    testImplementation(libs.mockk) // Note: Original was 1.13.10, catalog uses 1.13.11
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.junit.jupiter.api) // Note: Original was 5.9.2, catalog uses 5.10.2
    testRuntimeOnly(libs.junit.jupiter.engine) // Note: Original was 5.9.2, catalog uses 5.10.2

}

