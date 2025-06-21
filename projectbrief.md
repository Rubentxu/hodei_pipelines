# Project Brief: Hodei Pipelines

## Objetivos Principales

El objetivo de Hodei Pipelines es crear un sistema de CI/CD moderno, flexible y potente utilizando Kotlin. La meta es ofrecer una alternativa a soluciones existentes como Jenkins o GitLab CI, pero con un enfoque nativo de Kotlin que permita a los desarrolladores definir pipelines de una manera idiomática y segura.

El sistema debe ser capaz de ejecutar pipelines definidos como scripts de Kotlin (`.kts`), proporcionando un DSL (Domain-Specific Language) rico y extensible para describir las tareas de construcción, prueba y despliegue.

## Requisitos Clave

- **Ejecución de Scripts Dinámica:** El núcleo del sistema debe ser capaz de compilar y ejecutar scripts de pipeline de Kotlin en tiempo de ejecución.
- **DSL Inspirado en Gradle/Jenkins:** Proveer un DSL intuitivo que se sienta familiar para los desarrolladores de Kotlin, con conceptos como `tasks`, `stages`, y `steps`.
- **Arquitectura Hexagonal:** El sistema debe seguir una arquitectura limpia que separe el dominio, la aplicación y la infraestructura.
- **Extensibilidad:** El DSL y el sistema de ejecución deben ser extensibles para permitir la adición de nuevos pasos y funcionalidades personalizadas.
- **Aislamiento y Seguridad:** La ejecución de los pipelines debe ser segura y aislada para prevenir efectos secundarios no deseados.
