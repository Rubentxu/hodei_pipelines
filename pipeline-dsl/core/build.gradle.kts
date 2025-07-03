plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    `java-library`
}

dependencies {
    // Pipeline DSL standalone - minimal dependencies
    
    // Kotlin Core
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    
    // Kotlin Scripting para .pipeline.kts
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.compiler.embeddable)
    
    // Logging
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)


}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
