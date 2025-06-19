import org.gradle.api.file.DuplicatesStrategy
import com.google.protobuf.gradle.*
import com.google.gradle.osdetector.OsDetector

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // If using kotlinx.serialization for DB DTOs etc.
    alias(libs.plugins.protobuf)
    alias(libs.plugins.osdetector)
}


// Configure JUnit Platform for Kotest
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

dependencies {
    implementation(project(":backend:domain"))
    implementation(project(":backend:application"))

    implementation(libs.kotlinx.coroutines.core)
//    implementation("io.grpc:grpc-protobuf:1.58.0") meterlo en libs
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java.util)
    implementation(libs.kotlin.stdlib)


    // Testing
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotest.runner.junit5) // Note: Original was kotest-runner-junit5-jvm:5.8.0, catalog uses 5.8.1
    testImplementation(libs.kotest.assertions.core) // Note: Original was kotest-assertions-core-jvm:5.8.0, catalog uses 5.8.1
    testImplementation(libs.mockk) // Note: Original was 1.13.10, catalog uses 1.13.11
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.junit.jupiter.api) // Note: Original was 5.9.2, catalog uses 5.10.2
    testRuntimeOnly(libs.junit.jupiter.engine) // Note: Original was 5.9.2, catalog uses 5.10.2

}

tasks.jar {
//    manifest {
//        attributes["Main-Class"] = application.mainClass.get()
//    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// core/infrastructure/build.gradle.kts

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        val osClassifier = project.extensions.getByType(OsDetector::class.java).classifier

        // El plugin de Java es un ejecutable nativo y usa el clasificador del SO.
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}:${osClassifier}"
        }
        // El plugin de Kotlin es un JAR y usa el clasificador 'jdk8'.
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
            task.builtins {
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

