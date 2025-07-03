import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    `java-library`
    `maven-publish`
}

group = "dev.rubentxu.hodei.pipelines"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Protobuf & gRPC
    api(libs.protobuf.java)
    api(libs.protobuf.kotlin)
    api(libs.grpc.protobuf)
    api(libs.grpc.kotlin.stub)
    api(libs.kotlinx.coroutines.core)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
    
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") {}
                id("grpckt") {}
            }
            it.builtins {
                id("kotlin") {}
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

// Configuración para publicar el artefacto
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Task para limpiar los archivos generados
tasks.register("cleanGenerated") {
    doLast {
        delete("build/generated")
    }
}

tasks.clean {
    dependsOn("cleanGenerated")
}