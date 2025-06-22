plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

tasks.jar {
    archiveBaseName.set("worker-application")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":worker:infrastructure"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
}

application {
    mainClass.set("dev.rubentxu.hodei.pipelines.worker.application.PipelineWorkerAppKt")
}

tasks.test {
    useJUnitPlatform()
}