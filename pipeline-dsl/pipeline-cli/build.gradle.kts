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
    
    // Test dependencies
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation("org.junit.platform:junit-platform-suite-api:1.10.0")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation(libs.mockk)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    
    // Para tests de integración
    testImplementation("org.junit.platform:junit-platform-engine:1.10.0")
    testImplementation("org.junit.platform:junit-platform-commons:1.10.0")
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
