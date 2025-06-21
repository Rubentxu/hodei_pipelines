plugins {
    alias(libs.plugins.kotlin.jvm)
}

tasks.jar {
    archiveBaseName.set("worker-infrastructure")
}

dependencies {
    implementation(project(":core:infrastructure"))
    
    implementation(libs.grpc.netty.shaded)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.dependencies)
//    implementation(libs.kotlin.scripting.util)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}

tasks.test {
    useJUnitPlatform()
}

