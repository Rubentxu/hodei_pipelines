plugins {
    id("org.jetbrains.kotlin.jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

dependencies {
    implementation(project(":pipeline-dsl:core"))
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation(libs.kotlinx.coroutines.core)
    
    // Ktor Client for HTTP communication with orchestrator
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-websockets:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-logging:${libs.versions.ktor.get()}")
    
    // JSON serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

}

application {
    mainClass.set("dev.rubentxu.hodei.pipelines.dsl.cli.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.rubentxu.hodei.pipelines.dsl.cli.MainKt"
    }
}

tasks.jar {
    enabled = false
    dependsOn(tasks.shadowJar)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("pipeline-cli")
            mainClass.set("dev.rubentxu.hodei.pipelines.dsl.cli.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("--enable-preview")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+PrintClassInitialization")
            buildArgs.add("--initialize-at-build-time=kotlin")
            buildArgs.add("--initialize-at-build-time=kotlinx.coroutines")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
