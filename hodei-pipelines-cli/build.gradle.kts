plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("dev.rubentxu.hodei.cli.MainKt")
    applicationName = "hp"
}

dependencies {
    // CLI framework
    implementation(libs.clikt)
    
    // HTTP client for API communication
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Coroutines and serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    
    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.mockk)
    
    // For testing with orchestrator JAR
    testImplementation(project(":orchestrator"))
}

tasks.test {
    useJUnitPlatform()
}

// Configure Shadow JAR
tasks.shadowJar {
    archiveBaseName.set("hodei-pipelines-cli")
    archiveClassifier.set("all")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "dev.rubentxu.hodei.cli.MainKt"
    }
}

// Make build task depend on shadowJar
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Configure distribution for easy installation
distributions {
    main {
        distributionBaseName.set("hodei-pipelines-cli")
    }
}

// Task to create a convenient launcher script
tasks.register("createLauncher") {
    group = "distribution"
    description = "Creates a launcher script for the CLI"
    
    doLast {
        val launcherDir = File(layout.buildDirectory.asFile.get(), "launcher")
        launcherDir.mkdirs()
        
        val script = File(launcherDir, "hp")
        script.writeText("""#!/bin/bash
SCRIPT_DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" && pwd )"
java -jar "${'$'}SCRIPT_DIR/../libs/hodei-pipelines-cli-all.jar" "${'$'}@"
""")
        script.setExecutable(true)
        
        println("Launcher script created at: ${script.absolutePath}")
    }
}