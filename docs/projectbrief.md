# Project Brief: hodei-pipelines

## 1. Resumen del Proyecto

*(A completar: Proporcionar una descripción concisa del proyecto, su propósito y el problema que resuelve. Ejemplo: "Hodei-pipelines es un sistema distribuido para la ejecución de flujos de trabajo (pipelines) de CI/CD, diseñado para ser escalable y resiliente.")*

## 2. Objetivos Principales

- **O1**: Ejecutar trabajos (`Job`) de forma distribuida en un conjunto de workers.
- **O2**: Orquestar la asignación de trabajos mediante un `JobScheduler` con estrategias configurables (prioridad, FIFO, etc.).
- **O3**: Gestionar un `WorkerPool` de workers, monitorizando su estado y asignándoles trabajos.
- **O4**: Soportar el escalado dinámico y automático de workers (`ScalingPolicy`) basado en la carga de trabajo, utilizando plantillas (`WorkerTemplate`) inspiradas en Kubernetes.
- **O5**: Definir trabajos a través de `JobPayloads` flexibles, soportando al menos scripts de Kotlin y comandos de shell.
- **O6**: Asegurar una comunicación eficiente y robusta entre el `backend` y los `workers` (probablemente usando gRPC).

## 3. Alcance

### Funcionalidades Incluidas (En Alcance)

- Registro de workers en el servidor.
- Solicitud de ejecución de un pipeline.
- Envío de jobs a los workers disponibles.
- Recepción de logs y estado de la ejecución en tiempo real.

### Funcionalidades Excluidas (Fuera de Alcance)

- *(A completar: Listar funcionalidades que no se abordarán en la versión actual, como por ejemplo, una interfaz de usuario web, almacenamiento persistente de resultados de pipelines, etc.)*
