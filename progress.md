# Progress: Hodei Pipelines

## Funcionalidad Implementada

- **Versión Inicial del Worker:** Existe un worker capaz de recibir trabajos.
- **Ejecución de Scripts (JSR-223):** Se ha implementado una primera versión del `PipelineScriptExecutor` que utiliza JSR-223 para ejecutar scripts de Kotlin.
- **DSL Básico:** Se ha definido un DSL inicial con soporte para tareas y dependencias, similar a Gradle.

## Funcionalidad en Desarrollo

- **Motor de Scripting Avanzado:** Se está trabajando en la migración a la API `kotlin.script.experimental` para un DSL más potente.
