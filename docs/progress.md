# Project Progress

- **Fecha**: 2025-06-23
- **Estado**: Desarrollo activo.
- **Funcionalidad Implementada**: 
  - Modelo de Dominio del Pipeline (`Pipeline`, `Stage`, `Step`, `Agent`).
  - DSL para definición de pipelines (`pipeline`, `stage`, `sh`, `agent`, `checkout`).
  - **Arquitectura Extensible**: Refactorización del `PipelineRunner` para utilizar un patrón Strategy con `StepExecutor`, permitiendo añadir nuevos tipos de pasos sin modificar el núcleo del ejecutor.
  - **Manejo de Errores**: Implementación de la lógica `failFast` a nivel de etapa para detener la ejecución ante un fallo.
  - **Bloques `post`**: Añadida la capacidad de definir bloques `post` que se ejecutan siempre al finalizar una etapa, para acciones de limpieza.
  - Cargador de pipelines desde scripts Kotlin (`.kts`) (`PipelineLoader`).
  - Interfaz de Línea de Comandos (CLI) para ejecutar pipelines desde archivos.
- **Funcionalidad Verificada por BDD**: 
  - **Escenario (Definición):** "A user defines a simple pipeline...".
  - **Escenario (Ejecución):** "A user executes a simple pipeline".
  - **Escenario (Definición de Agente):** "A user defines a pipeline with a Docker agent".
  - **Escenario (Ejecución con Agente):** "A user executes a pipeline with a Docker agent".
  - **Escenario (Montaje de Workspace):** "A user executes a step that reads a file from the workspace".
  - **Escenario (Ejecución con Eventos):** "A user executes a pipeline and receives real-time events".
  - **Escenario (Carga de Pipeline):** "A user loads a pipeline from a .kts file".
  - **Escenario (CLI):** "A user executes a pipeline script from the CLI".
  - **Escenario (Arquitectura):** "The pipeline runner is refactored to be extensible".
  - **Escenario (Paso Extensible):** "A user executes a pipeline with a checkout step".
  - **Escenario (Manejo de Errores):** "A pipeline with a failing step stops execution".
  - **Escenario (Post-éxito):** "The post block is executed on stage success".
  - **Escenario (Post-fallo):** "The post block is executed on stage failure".
  - **Resultado:** Todos verificados con éxito.

El núcleo del DSL para la **definición** de pipelines está completo y validado.