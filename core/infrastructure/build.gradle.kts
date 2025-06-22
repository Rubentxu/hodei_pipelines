plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

tasks.jar {
    archiveBaseName.set("core-infrastructure")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:application"))
    
    api(libs.kotlinx.coroutines.core)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.kotlin)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
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
                create("kotlin")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}