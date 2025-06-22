# Estructura del Proyecto: hodei-pipelines

Este documento describe la organización de los directorios y archivos clave del proyecto `hodei-pipelines`, basada en la configuración de `settings.gradle.kts`.

```
hodei-pipelines/
├── .github/                     # Configuración de CI/CD con GitHub Actions
├── backend/
│   ├── application/             # Capa de aplicación del backend
│   └── infrastructure/          # Capa de infraestructura del backend (p. ej., controladores, repositorios)
├── core/
│   ├── application/             # Lógica de aplicación central (casos de uso)
│   ├── domain/                  # Lógica y modelos de dominio (entidades, eventos)
│   └── infrastructure/          # Infraestructura central (p. ej., gRPC, persistencia)
├── worker/
│   ├── application/             # Capa de aplicación del worker
│   └── infrastructure/          # Capa de infraestructura del worker
├── docs/                        # Documentación del proyecto (Banco de Registro)
├── gradle/
├── build.gradle.kts             # Script de build principal (raíz)
├── gradle.properties            # Propiedades de configuración de Gradle
├── gradlew
├── gradlew.bat
├── LICENSE
└── settings.gradle.kts          # Configuración de los módulos del proyecto Gradle
```

## Descripción de Módulos

El proyecto está organizado en un sistema multi-módulo de Gradle, siguiendo los principios de la Arquitectura Hexagonal.

- **`core`**: Contiene el núcleo desacoplado del sistema.
  - `core:domain`: Define las entidades, los eventos y las reglas de negocio más fundamentales del sistema. No tiene dependencias con otras capas.
  - `core:application`: Orquesta los flujos de datos y ejecuta la lógica de negocio a través de casos de uso. Depende de `core:domain`.
  - `core:infrastructure`: Proporciona implementaciones concretas para la comunicación (gRPC), la persistencia y otros servicios externos definidos en las capas internas.

- **`backend`**: Representa el componente servidor de la aplicación.
  - `backend:application`: Contiene los casos de uso específicos del backend.
  - `backend:infrastructure`: Implementa los adaptadores para el backend, como los puntos de entrada de la API (controladores gRPC) y las conexiones a bases de datos.

- **`worker`**: Representa el componente encargado de ejecutar tareas.
  - `worker:application`: Contiene los casos de uso para la ejecución de trabajos.
  - `worker:infrastructure`: Implementa los adaptadores para el worker, como la comunicación con el servidor y la ejecución de procesos.

- **`docs`**: Almacena toda la documentación generada por RubentxuAI.