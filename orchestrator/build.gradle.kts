plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "dev.rubentxu.hodei"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Shared proto module
    implementation(project(":shared:proto"))
    
    // Ktor Server Bundle
    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Kotlinx Bundle
    implementation(libs.bundles.kotlinx.libs)
    
    // Dependency Injection Bundle
    implementation(libs.bundles.koin.libs)
    
    // Error Handling Bundle
    implementation(libs.bundles.arrow.libs)
    
    // Logging Bundle
    implementation(libs.bundles.logging.libs)
    
    // Observability Bundle
    implementation(libs.bundles.observability.libs)
    
    // Security Bundle
    implementation(libs.bundles.security.libs)
    
    // gRPC Bundle
    implementation(libs.bundles.grpc.libs)
    
    // Docker
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
    implementation("com.github.docker-java:docker-java:3.4.1")
    
    // CLI
    implementation(libs.clikt)
    
    // Testing
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.framework.datatest)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.extensions.koin)
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation(libs.mockk)
    testImplementation(libs.koin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.content.negotiation)
    
    // Add missing Ktor dependencies for tests
    testImplementation(libs.bundles.ktor.server)
    
    // Add security dependencies for tests
    testImplementation(libs.bundles.security.libs)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.rubentxu.hodei.HodeiPipelinesApplicationKt")
}

// Task for building worker executable
tasks.register<Jar>("workerJar") {
    group = "build"
    description = "Build worker executable JAR"
    
    archiveBaseName.set("hodei-worker")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "dev.rubentxu.hodei.worker.WorkerApplicationKt"
    }
    
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

