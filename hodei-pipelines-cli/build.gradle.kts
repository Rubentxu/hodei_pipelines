plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.graalvm.native)
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
    implementation(libs.ktor.client.java)
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

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("hp")
            mainClass.set("dev.rubentxu.hodei.cli.MainKt")
            
            buildArgs.addAll(
                "--no-fallback",                    // No fallback to JVM
                "--enable-preview",                 // Enable preview features
                "--install-exit-handlers",          // Install proper exit handlers
                "--report-unsupported-elements-at-runtime",
                "-H:+ReportExceptionStackTraces",   // Report exception stack traces
                "-H:+AddAllCharsets",               // Add all charsets
                "-H:+UnlockExperimentalVMOptions",  // Unlock experimental options
                "-H:IncludeResources=.*\\.properties", // Include properties files
                "-H:IncludeResources=.*\\.json",    // Include JSON files
                "-H:IncludeResources=.*\\.yml",     // Include YAML files
                "-H:IncludeResources=.*\\.yaml",    // Include YAML files
                "--initialize-at-build-time=org.slf4j,ch.qos.logback,kotlin.DeprecationLevel", // Initialize logging and Kotlin enums at build time
                "--initialize-at-run-time=io.ktor,kotlinx.coroutines,io.netty,java.net.http" // Initialize at runtime
            )
            
            // Runtime arguments for the native image
            runtimeArgs.addAll(
                "--spring.native.remove-unused-autoconfig=true"
            )
        }
    }
}

// Create resources for native image reflection
tasks.register("generateNativeReflectionConfig") {
    group = "native"
    description = "Generate reflection configuration for native image"
    
    doLast {
        val resourcesDir = File(layout.buildDirectory.asFile.get(), "native/generated")
        resourcesDir.mkdirs()
        
        val reflectConfig = File(resourcesDir, "reflect-config.json")
        reflectConfig.writeText("""
[
  {
    "name": "dev.rubentxu.hodei.cli.MainKt",
    "methods": [{"name": "main", "parameterTypes": ["java.lang.String[]"]}]
  },
  {
    "name": "kotlinx.serialization.json.Json",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "kotlinx.serialization.json.JsonElement",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.AuthToken",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.User",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.LoginResponse",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.ResourcePool",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.Job",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.Worker",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  },
  {
    "name": "dev.rubentxu.hodei.cli.client.Template",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  }
]
        """.trimIndent())
        
        val resourceConfig = File(resourcesDir, "resource-config.json")
        resourceConfig.writeText("""
{
  "resources": {
    "includes": [
      {"pattern": "\\QMETA-INF/services\\E.*"},
      {"pattern": "\\Qlogback.xml\\E"},
      {"pattern": "\\Qlogback-spring.xml\\E"},
      {"pattern": "\\Qapplication.properties\\E"},
      {"pattern": "\\Qapplication.yml\\E"},
      {"pattern": "\\Qapplication.yaml\\E"}
    ]
  },
  "bundles": []
}
        """.trimIndent())
        
        println("Native reflection config generated at: ${reflectConfig.absolutePath}")
        println("Native resource config generated at: ${resourceConfig.absolutePath}")
    }
}

// Task to build native executable for current platform
tasks.register("buildNativeExecutable") {
    group = "native"
    description = "Build native executable for current platform"
    dependsOn("generateNativeReflectionConfig")
    dependsOn("nativeCompile")
}

// Task to build native executables for all platforms
tasks.register("buildAllNativeBinaries") {
    group = "native"
    description = "Build native executables for all platforms"
    dependsOn("buildNativeExecutable")
    
    doLast {
        println("✅ Native binary built successfully!")
        println("📁 Binary location: ${layout.buildDirectory.asFile.get()}/native/nativeCompile/")
        println("🚀 You can now distribute the 'hp' binary standalone")
    }
}

// Create platform-specific distributions
tasks.register("createNativeDistributions") {
    group = "distribution"
    description = "Create platform-specific distributions with native binaries"
    dependsOn("buildNativeExecutable")
    
    doLast {
        val distDir = File(layout.buildDirectory.asFile.get(), "distributions/native")
        distDir.mkdirs()
        
        val nativeBinary = File(layout.buildDirectory.asFile.get(), "native/nativeCompile/hp")
        if (nativeBinary.exists()) {
            // Create distribution structure
            val linuxDir = File(distDir, "linux-x64")
            linuxDir.mkdirs()
            
            // Copy binary
            nativeBinary.copyTo(File(linuxDir, "hp"), overwrite = true)
            File(linuxDir, "hp").setExecutable(true)
            
            // Create README
            File(linuxDir, "README.md").writeText("""
# Hodei Pipelines CLI - Native Binary

This is a native standalone binary of the Hodei Pipelines CLI.

## Installation

1. Make sure the binary is executable:
   ```bash
   chmod +x hp
   ```

2. Add to your PATH or run directly:
   ```bash
   ./hp --help
   ```

## Usage

```bash
# Login to orchestrator
./hp login http://localhost:8080

# Submit a job
./hp job submit pipeline.kts

# Check status
./hp status
```

## Requirements

- No JVM required
- Linux x64 (this binary)
- No additional dependencies

For more information, visit: https://github.com/rubentxu/hodei-pipelines
            """.trimIndent())
            
            println("✅ Native distribution created: ${linuxDir.absolutePath}")
        } else {
            println("❌ Native binary not found. Run 'gradle buildNativeExecutable' first.")
        }
    }
}