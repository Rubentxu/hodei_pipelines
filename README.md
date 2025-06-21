# Hodei-Pipelines: Distributed Pipeline Orchestrator


**Hodei-Pipelines** is a modern, distributed, and scalable system for orchestrating and executing job pipelines. Built with Kotlin and gRPC, it leverages a clean, hexagonal architecture to ensure maintainability, testability, and separation of concerns.

## ✨ Key Features

- **Distributed Job Execution**: Run jobs (scripts or commands) on a pool of scalable workers.
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

## 🛠️ Technology Stack

- **Language**: [Kotlin](https://kotlinlang.org/) with Coroutines for asynchronous programming.
- **Communication**: [gRPC](https://grpc.io/) with [Protocol Buffers](https://developers.google.com/protocol-buffers) for high-performance RPC.
- **Build System**: [Gradle](https://gradle.org/) with the Kotlin DSL.
- **Logging**: [KotlinLogging](https://github.com/MicroUtils/kotlin-logging).

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

## 📚 In-Depth Documentation

This project uses a "Registro de Conocimiento" (Knowledge Registry) to maintain comprehensive documentation. All detailed documentation is located in the `/docs` directory.

- **[Project Brief](./docs/projectbrief.md)**: High-level goals and requirements.
- **[Product Context](./docs/productContext.md)**: The "why" behind the project and user experience goals.
- **[System Patterns](./docs/systemPatterns.md)**: Detailed architecture, diagrams, and design patterns.
- **[Project Structure](./docs/project_structure.md)**: A complete breakdown of all modules and key directories.
- **[Tech Context](./docs/techContext.md)**: Details on the technology stack and development tools.
- **[Active Context](./docs/activeContext.md)**: Current work focus, next steps, and active decisions.

## 🤝 Contributing

Contributions are welcome! Please refer to the `CONTRIBUTING.md` file for guidelines. (Note: This file is a placeholder).

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details. (Note: This file is a placeholder).
