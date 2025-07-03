plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

tasks.jar {
    archiveBaseName.set("worker-infrastructure")
}

dependencies {
    // Project dependencies
    implementation(project(":worker:core"))
    implementation(project(":shared:proto"))
    implementation(project(":pipeline-dsl:core"))

    // gRPC dependencies
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    
    // Kotlin scripting dependencies
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.compiler.embeddable)
    
    // Logging
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    // Testing with kotest
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.framework.datatest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}

