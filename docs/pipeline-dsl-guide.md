# Pipeline DSL Guide

## Overview

The Hodei-Pipelines Pipeline DSL provides a Jenkins-like declarative syntax for defining build pipelines in Kotlin. It supports stages, parallel execution, dynamic scripting, and extensive customization through extensions.

## Basic Pipeline Structure

```kotlin
pipeline {
    stage("Preparation") {
        script {
            println("Setting up the environment...")
            sh("git clean -fdx")
        }
    }
    
    stage("Build") {
        parallel {
            task("Compile") {
                sh("./gradlew compileKotlin")
            }
            task("Generate Resources") {
                sh("./gradlew processResources")
            }
        }
    }
    
    stage("Test") {
        script {
            sh("./gradlew test")
            publishTestResults("**/build/test-results/test/TEST-*.xml")
        }
    }
}
```

## Execution Strategies

The Pipeline DSL supports multiple execution strategies through the Strategy pattern:

### 1. KotlinScriptingStrategy
Uses the Kotlin Scripting API for dynamic script execution with full DSL access.

**Features:**
- Full Kotlin language support
- Access to Pipeline DSL functions
- Dynamic compilation and execution
- Real-time output streaming

**Example:**
```kotlin
val strategy = KotlinScriptingStrategy(
    libraryManager = libraryManager,
    extensionManager = extensionManager,
    securityManager = securityManager
)
```

### 2. CompilerEmbeddableStrategy
Uses kotlin-compiler-embeddable for compilation and execution.

**Features:**
- Optimized compilation
- Better performance for large scripts
- Advanced Kotlin feature support

### 3. SystemCommandStrategy
Direct system command execution for shell scripts and external tools.

**Features:**
- Direct shell access
- Environment variable support
- Process isolation

## Security Features

### Dangerous Code Detection

The security system automatically detects and prevents execution of potentially harmful code patterns:

```kotlin
// These patterns are automatically blocked:
System.exit(1)                    // System exit calls
Runtime.getRuntime().exec(...)    // Runtime execution
ProcessBuilder(...)               // Process creation
File(...).delete()               // File operations (configurable)
```

### Security Policies

Configure security policies to control what operations are allowed:

```kotlin
val securityConfig = PipelineSecurityConfig(
    allowSystemExit = false,
    allowFileOperations = true,
    allowNetworkAccess = false,
    customDangerousPatterns = listOf(
        "myCustomDangerousPattern.*"
    )
)
```

## Library Management

### Dynamic JAR Loading

Load external dependencies at runtime:

```kotlin
libraryManager.loadLibrary(LibraryInfo(
    id = "my-extension",
    jarPath = "/path/to/extension.jar",
    version = "1.0.0"
))
```

### Dependency Resolution

The library manager handles version conflicts and dependency resolution automatically:

```kotlin
// Multiple versions of the same library
libraryManager.loadLibrary(LibraryInfo("commons-lang", "/path/v2.jar", "2.6"))
libraryManager.loadLibrary(LibraryInfo("commons-lang", "/path/v3.jar", "3.12"))
// Automatically resolves to the latest compatible version
```

## Extension System

### Creating Extensions

Create custom extensions by implementing the Extension interface:

```kotlin
class MyCustomExtension : Extension {
    override val id = "my-custom-extension"
    override val version = "1.0.0"
    
    override fun initialize(context: ExtensionContext) {
        // Extension initialization
    }
    
    override fun provideFunctions(): Map<String, Any> = mapOf(
        "myCustomFunction" to ::myCustomFunction
    )
    
    private fun myCustomFunction(param: String): String {
        return "Processed: $param"
    }
}
```

### Using Extensions

```kotlin
// Load extension
extensionManager.loadExtension(MyCustomExtension())

// Use in pipeline
pipeline {
    stage("Custom Processing") {
        script {
            val result = myCustomFunction("test data")
            println("Result: $result")
        }
    }
}
```

## Event Streaming

The Pipeline DSL emits real-time events via gRPC for monitoring and integration:

### Event Types

1. **Stage Events**
   - `STAGE_STARTED`
   - `STAGE_COMPLETED`
   - `STAGE_FAILED`

2. **Task Events**
   - `TASK_STARTED`
   - `TASK_COMPLETED`
   - `TASK_FAILED`

3. **Pipeline Events**
   - `PIPELINE_STARTED`
   - `PIPELINE_COMPLETED`
   - `PIPELINE_FAILED`

4. **Custom Events**
   - User-defined events via `emit()` function

### Event Structure

```kotlin
data class PipelineEvent(
    val type: EventType,
    val timestamp: Instant,
    val stageId: String?,
    val taskId: String?,
    val message: String,
    val metadata: Map<String, Any> = emptyMap()
)
```

## Built-in Functions

The Pipeline DSL provides several built-in functions:

### Command Execution
```kotlin
sh("echo 'Hello World'")              // Execute shell command
bat("echo Hello World")               // Execute batch command (Windows)
```

### File Operations
```kotlin
readFile("path/to/file.txt")          // Read file content
writeFile("path/to/file.txt", content) // Write file content
copyFile("source.txt", "dest.txt")    // Copy files
```

### Parallel Execution
```kotlin
parallel {
    task("Task 1") { /* ... */ }
    task("Task 2") { /* ... */ }
    task("Task 3") { /* ... */ }
}
```

### Environment Variables
```kotlin
env("PATH")                           // Get environment variable
setEnv("MY_VAR", "value")            // Set environment variable
```

### Artifact Publishing
```kotlin
publishArtifact("build/libs/*.jar")   // Publish build artifacts
archiveFiles("**/*.log")             // Archive files
```

## Testing Pipeline Scripts

### Unit Testing

Test pipeline scripts using the provided test utilities:

```kotlin
@Test
fun `should execute pipeline successfully`() = runTest {
    val script = """
        pipeline {
            stage("Test") {
                script {
                    println("Testing...")
                }
            }
        }
    """
    
    val executor = PipelineScriptExecutor(
        strategies = mapOf("SCRIPT" to mockStrategy),
        eventPublisher = mockEventPublisher
    )
    
    val job = createTestJob(script)
    val events = executor.execute(job, WorkerId("test-worker")).toList()
    
    assertThat(events).hasSize(3) // Started, Output, Completed
}
```

### Integration Testing

The project includes comprehensive integration tests that use embedded gRPC servers:

```kotlin
@Test
fun `should handle complete pipeline execution`() = runTest {
    val server = EmbeddedGrpcServer().start()
    val worker = PipelineWorker(
        workerId = "test-worker",
        serverHost = "localhost",
        serverPort = server.port,
        scriptExecutor = realScriptExecutor
    )
    
    // Test complete pipeline execution flow
}
```

## Best Practices

### 1. Error Handling
```kotlin
pipeline {
    stage("Build") {
        try {
            sh("./gradlew build")
        } catch (e: Exception) {
            emit(PipelineEvent.error("Build failed: ${e.message}"))
            throw e
        }
    }
}
```

### 2. Resource Cleanup
```kotlin
pipeline {
    try {
        stage("Setup") {
            sh("setup-resources.sh")
        }
        stage("Execute") {
            sh("run-tests.sh")
        }
    } finally {
        stage("Cleanup") {
            sh("cleanup-resources.sh")
        }
    }
}
```

### 3. Conditional Execution
```kotlin
pipeline {
    stage("Deploy") {
        if (env("BRANCH_NAME") == "main") {
            sh("deploy-to-production.sh")
        } else {
            println("Skipping deployment for branch: ${env("BRANCH_NAME")}")
        }
    }
}
```

### 4. Artifact Management
```kotlin
pipeline {
    stage("Build") {
        sh("./gradlew build")
        publishArtifact("build/libs/*.jar")
    }
    
    stage("Test") {
        // Artifacts from previous stage are automatically available
        sh("java -jar build/libs/app.jar --test")
    }
}
```

## Performance Considerations

1. **Script Compilation**: Use CompilerEmbeddableStrategy for large or complex scripts
2. **Parallel Execution**: Leverage parallel tasks for independent operations
3. **Artifact Caching**: Enable artifact caching for frequently used dependencies
4. **Resource Management**: Properly clean up resources in finally blocks
5. **Event Throttling**: Be mindful of event emission frequency for large pipelines

## Troubleshooting

### Common Issues

1. **Script Compilation Errors**
   - Check Kotlin syntax and imports
   - Verify that required libraries are loaded
   - Review security policy restrictions

2. **Permission Denied Errors**
   - Check security policy configuration
   - Verify file system permissions
   - Review dangerous code detection patterns

3. **Performance Issues**
   - Profile pipeline execution using event streaming
   - Consider using CompilerEmbeddableStrategy for large scripts
   - Optimize parallel task distribution

### Debugging

Enable debug logging to troubleshoot issues:

```kotlin
// In pipeline script
println("Debug: Current working directory = ${System.getProperty("user.dir")}")
println("Debug: Environment variables = ${System.getenv()}")
```

Monitor pipeline events for detailed execution flow:

```kotlin
// Event monitoring
eventPublisher.subscribe { event ->
    when (event.type) {
        EventType.STAGE_FAILED -> logger.error("Stage failed: ${event.message}")
        EventType.TASK_FAILED -> logger.error("Task failed: ${event.message}")
        else -> logger.info("Event: ${event.type} - ${event.message}")
    }
}
```