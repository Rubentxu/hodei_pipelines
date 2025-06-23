# Project Structure

La estructura del proyecto es un monorepo multi-módulo gestionado por Gradle, organizado siguiendo los principios de la Arquitectura Hexagonal.

```
.hodei-pipelines/
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradle/
├── docs/
├── core/
├── backend/
├── worker/
└── pipeline-dsl/  <-- Módulo principal del DSL
    ├── core/      <-- Modelo de dominio y motor de ejecución
    └── cli/       <-- Interfaz de línea de comandos
```

## Módulos del Pipeline

Para la nueva funcionalidad del DSL, se ha creado una estructura de módulos dedicada:

- **pipeline-dsl**: Módulo agrupador.
  - `core`: Contendrá el modelo de dominio (`Pipeline`, `Stage`, `Step`), la implementación del DSL y el motor de ejecución. Esta será la librería principal.
  - `cli`: Una aplicación de línea de comandos para ejecutar pipelines definidos con el DSL. Dependerá de `core`.