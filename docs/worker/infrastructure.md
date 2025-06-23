# Documentación de Clases – worker/infrastructure

Este documento describe la clase principal del submódulo `worker/infrastructure`, su propósito, contexto y relaciones con el resto del sistema, explicando también por qué utiliza cada dependencia.

---

## worker.infrastructure.worker.PipelineWorker
**Descripción ampliada:**
`PipelineWorker` representa un agente autónomo dentro de la arquitectura distribuida de Hodei Pipelines. Su objetivo es ejecutar trabajos (jobs) de pipelines de forma remota, gestionando la comunicación con el servidor central, la ejecución de scripts, la transferencia de artefactos y el reporte de estados. Permite escalar horizontalmente la ejecución de pipelines y desacoplar la lógica de ejecución del servidor principal, aportando robustez y flexibilidad al sistema.

**Relaciones y por qué se usan:**
- **`PipelineScriptExecutor`**: Se utiliza para delegar la ejecución real de scripts y comandos definidos en los trabajos. Esto permite separar la lógica de orquestación (coordinada por el worker) de la lógica de ejecución específica, facilitando la extensibilidad y el testeo.
- **`JobExecutorServiceCoroutineStub` y `WorkerManagementServiceCoroutineStub` (gRPC)**: Permiten la comunicación eficiente y asíncrona con el servidor central, recibiendo trabajos, reportando estados y gestionando la vida del worker. Son esenciales para la arquitectura distribuida y la coordinación entre múltiples agentes.
- **Tipos de dominio (`Job`, `JobDefinition`, `JobId`, `JobPayload`, `JobStatus`, etc.)**: Se usan para mantener la coherencia y la separación de responsabilidades entre la infraestructura y la lógica de negocio. El worker traduce estos tipos a los mensajes gRPC y viceversa, asegurando que los datos fluyen correctamente entre capas.
- **Funciones de mapeo (entre modelos de dominio y protobuf)**: Permiten transformar los datos entre el modelo interno y el externo (gRPC), asegurando la interoperabilidad y la correcta interpretación de la información.
- **`ArtifactDownload` y `CachedArtifact`**: Se utilizan para gestionar la descarga, almacenamiento temporal y verificación de artefactos (archivos generados o requeridos por los jobs). Esto garantiza la integridad y disponibilidad de los artefactos durante la ejecución distribuida.

**Resumen:**
`PipelineWorker` es un componente clave que integra la infraestructura técnica (red, gRPC, artefactos) con el dominio de ejecución de pipelines, permitiendo la ejecución remota, segura y escalable de trabajos en la plataforma Hodei Pipelines.

---

> _Esta documentación cubre la clase principal del submódulo `worker/infrastructure`. La clase `PipelineWorker` es el punto de integración entre la infraestructura (gRPC, red, artefactos) y el dominio de ejecución de pipelines, habilitando la ejecución distribuida y escalable._
