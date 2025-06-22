plugins {
    alias(libs.plugins.kotlin.jvm)
}

tasks.jar {
    archiveBaseName.set("core-application")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}