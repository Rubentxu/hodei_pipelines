# Hodei-Pipelines: Distributed Pipeline Orchestrator


**Hodei-Pipelines** is a modern, distributed, and scalable system for orchestrating and executing job pipelines. Built with Kotlin and gRPC, it leverages a clean, hexagonal architecture to ensure maintainability, testability, and separation of concerns.

## ✨ Key Features

- **Distributed Job Execution**: Run jobs (scripts or commands) on a pool of scalable workers.
- **Strategy Pattern for Job Execution**: Multiple execution strategies including Kotlin scripting, compiler-embeddable, and system commands.
- **Pipeline DSL**: Jenkins-like Pipeline DSL for defining complex build pipelines with stages, steps, and parallel execution.
- **Security Sandbox**: Configurable security policies with dangerous code detection and prevention.
- **Library Management**: Dynamic JAR loading and dependency management for pipeline extensions.
- **Extension System**: Third-party plugin support for extending pipeline functionality.
- **Event Streaming**: Real-time pipeline events via gRPC for monitoring and integration.
- **Artifact Management**: Advanced artifact caching, compression, and transfer capabilities.
- **Hexagonal Architecture**: A clean separation between the core domain logic and infrastructure details (e.g., databases, network protocols).
- **gRPC-based Communication**: Efficient and strongly-typed communication between the central server and workers using Protocol Buffers.
- **Dynamic Worker Pools**: Manage and scale pools of workers based on configurable policies.
- **Advanced Job Scheduling**: Sophisticated scheduling strategies to assign jobs to the most suitable workers.
- **Automatic Worker Scaling**: Policies for automatically scaling worker resources up or down based on demand, inspired by Kubernetes.

## 🏛️ Architecture Overview

The project follows a strict **Hexagonal (Ports and Adapters) Architecture**. This isolates the core business logic from external concerns.

- **`core`**: Contains the heart of the application.
  - **`domain`**: Defines the business entities, rules, and the all-important **ports** (interfaces) that the domain needs to function.
  - **`application`**: Implements the use cases that orchestrate the domain logic.
  - **`infrastructure`**: Provides in-memory implementations of the ports for testing and standalone operation.
- **`backend`**: The central server component. It contains gRPC adapters that expose the application's use cases to the network.
- **`worker`**: The client component that registers with the server, receives jobs, executes them, and reports back the results.

For a deep dive into the architecture, component diagrams, and domain model, please see the [**System Patterns Document**](./docs/systemPatterns.md).

## 🔧 Pipeline DSL Features

### Kotlin Script Execution
Execute Kotlin scripts with full access to the Pipeline DSL:

```kotlin
pipeline {
    stage("Build") {
        script {
            println("Building the project...")
            sh("./gradlew build")
        }
    }
    stage("Test") {
        parallel {
            task("Unit Tests") {
                sh("./gradlew test")
            }
            task("Integration Tests") {
                sh("./gradlew integrationTest")
            }
        }
    }
}
```

### Execution Strategies
- **KotlinScriptingStrategy**: Execute Kotlin scripts using the Kotlin Scripting API
- **CompilerEmbeddableStrategy**: Compile and execute Kotlin code using kotlin-compiler-embeddable
- **SystemCommandStrategy**: Execute system commands and shell scripts

### Security Features
- **Dangerous Code Detection**: Automatically detects and prevents execution of potentially harmful code patterns
- **Configurable Security Policies**: Fine-grained control over what operations are allowed
- **Sandboxed Execution**: Isolated execution environment for scripts

### Library Management
- **Dynamic JAR Loading**: Load and manage external JAR dependencies at runtime
- **Version Conflict Resolution**: Handle dependency conflicts automatically
- **Extension Loading**: Support for third-party extensions and plugins

## 🛠️ Technology Stack

- **Language**: [Kotlin](https://kotlinlang.org/) with Coroutines for asynchronous programming.
- **Script Execution**: Kotlin Scripting API and kotlin-compiler-embeddable for dynamic code execution.
- **Communication**: [gRPC](https://grpc.io/) with [Protocol Buffers](https://developers.google.com/protocol-buffers) for high-performance RPC.
- **Build System**: [Gradle](https://gradle.org/) with the Kotlin DSL.
- **Testing**: JUnit 5, Mockito, and embedded gRPC servers for comprehensive integration testing.
- **Logging**: [KotlinLogging](https://github.com/MicroUtils/kotlin-logging).
- **Security**: Custom security sandbox with configurable policies.
- **Compression**: GZIP support for artifact transfer optimization.

For more details on the technology and tools, refer to the [**Tech Context Document**](./docs/techContext.md).

## 🚀 Getting Started

### Prerequisites

- JDK 17 or higher.
- Gradle.

### Build

To build the entire project and run all checks, execute the following command from the root directory:

```bash
./gradlew build
```

### Run

1.  **Start the Server**: Run the `main` function in `backend/application/src/main/kotlin/dev/rubentxu/hodei/pipelines/application/HodeiPipelinesServer.kt`.
2.  **Start a Worker**: Run the `main` function in `worker/application/src/main/kotlin/dev/rubentxu/hodei/pipelines/worker/application/PipelineWorkerApp.kt`.

## 🧪 Testing the Pipeline DSL

### Running the Test Suite

Execute all tests to verify functionality:

```bash
# Run all tests
./gradlew test

# Run only worker tests  
./gradlew :worker:infrastructure:test

# Run tests with detailed output
./gradlew test --info
```

### Manual Testing with Examples

#### 1. Basic Pipeline Example

Create a simple Kotlin script file `test-pipeline.kts`:

```kotlin
pipeline {
    stage("Hello World") {
        script {
            println("Hello from Hodei-Pipelines!")
            sh("echo 'System info:'")
            sh("uname -a")
        }
    }
}
```

#### 2. Multi-Stage Pipeline

```kotlin
pipeline {
    stage("Preparation") {
        script {
            println("Setting up environment...")
            setEnv("BUILD_NUMBER", "123")
            setEnv("PROJECT_NAME", "hodei-pipelines")
        }
    }
    
    stage("Build") {
        parallel {
            task("Compile") {
                println("Compiling sources...")
                sh("echo 'Compiling...'")
            }
            task("Resources") {
                println("Processing resources...")
                sh("echo 'Processing resources...'")
            }
        }
    }
    
    stage("Test") {
        script {
            println("Running tests for project: ${env("PROJECT_NAME")}")
            println("Build number: ${env("BUILD_NUMBER")}")
            sh("echo 'All tests passed!'")
        }
    }
}
```

#### 3. Error Handling Pipeline

```kotlin
pipeline {
    stage("Safe Operations") {
        try {
            script {
                println("Performing safe operations...")
                sh("echo 'This will succeed'")
            }
        } catch (e: Exception) {
            println("Unexpected error: ${e.message}")
        }
    }
    
    stage("Error Demonstration") {
        try {
            script {
                // This will be blocked by security
                System.exit(1)
            }
        } catch (e: SecurityException) {
            println("Security policy prevented dangerous operation: ${e.message}")
        }
    }
}
```

### Integration Testing

The project includes comprehensive integration tests that demonstrate real usage:

#### Running Integration Tests

```bash
# Run specific integration test
./gradlew :worker:infrastructure:test --tests "*MinimalIntegrationTest*"

# Run worker registration tests
./gradlew :worker:infrastructure:test --tests "*WorkerRegistrationIntegrationTest*"

# Run execution strategy tests  
./gradlew :worker:infrastructure:test --tests "*JobExecutionStrategyTest*"
```

#### Test Coverage Report

Generate test coverage reports:

```bash
./gradlew test jacocoTestReport
open worker/infrastructure/build/reports/jacoco/test/html/index.html
```

### Testing Different Execution Strategies

#### 1. KotlinScriptingStrategy Test

```kotlin
// This is tested automatically, but you can see it in:
// worker/infrastructure/src/test/kotlin/.../execution/JobExecutionStrategyTest.kt

@Test
fun `should execute Kotlin script using KotlinScriptingStrategy`() = runTest {
    val script = """
        pipeline {
            stage("Kotlin Script Test") {
                script {
                    println("Testing Kotlin Scripting Strategy")
                    val result = (1..5).sum()
                    println("Sum of 1-5: ${'$'}result")
                }
            }
        }
    """.trimIndent()
    
    // Test execution...
}
```

#### 2. Security Policy Testing

```kotlin
@Test  
fun `should block dangerous code patterns`() = runTest {
    val dangerousScript = """
        pipeline {
            stage("Dangerous Operations") {
                script {
                    System.exit(1) // This should be blocked
                }
            }
        }
    """.trimIndent()
    
    // Verify security exception is thrown...
}
```

### Performance Testing

#### Benchmark Pipeline Execution

```bash
# Run performance tests
./gradlew :worker:infrastructure:test --tests "*PerformanceTest*"

# Test with larger scripts
./gradlew :worker:infrastructure:test -Dtest.script.size=large
```

#### Memory Usage Testing

```kotlin
// Monitor memory usage during pipeline execution
pipeline {
    stage("Memory Test") {
        script {
            val runtime = Runtime.getRuntime()
            println("Used memory: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB")
            
            // Create some objects to test memory
            val largeList = (1..10000).toList()
            println("Created list with ${largeList.size} elements")
        }
    }
}
```

### Debugging and Troubleshooting

#### Enable Debug Logging

Add to your test configuration:

```kotlin
// In test setup
System.setProperty("kotlin.script.classpath", System.getProperty("java.class.path"))
System.setProperty("logging.level.dev.rubentxu.hodei.pipelines", "DEBUG")
```

#### Test Event Streaming

```kotlin
@Test
fun `should emit pipeline events`() = runTest {
    val events = mutableListOf<JobExecutionEvent>()
    
    // Collect events during execution
    executor.execute(job, workerId).collect { event ->
        events.add(event)
        println("Event: ${event::class.simpleName}")
    }
    
    // Verify events were emitted
    assertThat(events).hasSize(expectedEventCount)
}
```

### Load Testing

#### Multiple Workers

```bash
# Start multiple workers for load testing
./gradlew :worker:infrastructure:test --tests "*MultipleWorkersTest*"
```

#### Concurrent Pipeline Execution

```kotlin
@Test
fun `should handle concurrent pipeline execution`() = runTest {
    val workers = (1..5).map { createWorker("worker-$it") }
    val jobs = (1..10).map { createTestJob("job-$it") }
    
    // Execute jobs concurrently
    workers.forEach { it.start() }
    // Submit jobs and verify execution
}
```

### Monitoring Test Results

#### Real-time Test Output

```bash
# Watch test execution in real-time
./gradlew test --continuous

# Run tests with gradle daemon for faster execution
./gradlew test --daemon
```

#### Test Reports

After running tests, view detailed reports:

```bash
# Open test report in browser (Linux/macOS)
open worker/infrastructure/build/reports/tests/test/index.html

# Or check the terminal output for direct file path
```

### Custom Test Scenarios

Create your own test scenarios by extending the existing test infrastructure:

```kotlin
class CustomPipelineTest : IntegrationTestBase() {
    
    @Test
    fun `should execute custom pipeline scenario`() = runTest {
        val customScript = """
            pipeline {
                // Your custom pipeline logic here
            }
        """.trimIndent()
        
        val result = executeScript(customScript)
        // Assert your expectations
    }
}
```

This comprehensive testing approach ensures that all Pipeline DSL features work correctly and provides examples for users to understand the system capabilities.

## 📚 In-Depth Documentation

This project uses a "Registro de Conocimiento" (Knowledge Registry) to maintain comprehensive documentation. All detailed documentation is located in the `/docs` directory.

- **[Project Brief](./docs/projectbrief.md)**: High-level goals and requirements.
- **[Product Context](./docs/productContext.md)**: The "why" behind the project and user experience goals.
- **[System Patterns](./docs/systemPatterns.md)**: Detailed architecture, diagrams, and design patterns.
- **[Pipeline DSL Guide](./docs/pipeline-dsl-guide.md)**: Comprehensive guide to the Pipeline DSL features and usage.
- **[Project Structure](./docs/project_structure.md)**: A complete breakdown of all modules and key directories.
- **[Tech Context](./docs/techContext.md)**: Details on the technology stack and development tools.
- **[Active Context](./docs/activeContext.md)**: Current work focus, next steps, and active decisions.

## 🤝 Contributing

Contributions are welcome! Please refer to the `CONTRIBUTING.md` file for guidelines. (Note: This file is a placeholder).

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details. (Note: This file is a placeholder).
