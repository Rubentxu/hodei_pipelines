plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

tasks.jar {
    archiveBaseName.set("backend-application")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":core:infrastructure"))
    implementation(project(":backend:infrastructure"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    implementation(libs.grpc.services)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
}

application {
    mainClass.set("dev.rubentxu.hodei.pipelines.application.HodeiPipelinesServerKt")
}

tasks.test {
    useJUnitPlatform()
}