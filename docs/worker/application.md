# Documentación de Clases – worker/application

Este documento describe cada clase encontrada en el submódulo `worker/application`, su propósito, contexto y relaciones con otras clases del sistema, explicando también por qué utiliza cada dependencia.

---

## worker.application.worker.dsl.DefaultLibraryManager
**Descripción ampliada:**
Implementación por defecto de `LibraryManager`. Gestiona la descarga, verificación, carga y cacheo de librerías externas (por ejemplo, JARs) necesarias para la ejecución de pipelines. Permite que los pipelines sean extensibles y seguros, asegurando que las dependencias externas estén disponibles y verificadas antes de la ejecución.

**Relaciones y por qué se usan:**
- **`LibraryManager` (interfaz de dominio):** Implementa esta interfaz para cumplir el contrato de gestión de librerías definido en el dominio, permitiendo sustituir la implementación si se requiere.
- **`LibraryRepository`:** Se utiliza para acceder y almacenar definiciones de librerías, desacoplando el acceso a datos de la lógica de negocio.
- **`PipelineSecurityManager`:** Garantiza que las librerías descargadas y cargadas cumplen con las políticas de seguridad, protegiendo la ejecución de código externo.
- **Entidades como `LibraryReference`, `LibraryDefinition`, excepciones:** Permiten modelar el ciclo de vida y los posibles errores de las librerías de manera robusta y explícita.

---

## worker.application.worker.dsl.extensions.DefaultExtensionManager
**Descripción ampliada:**
Implementación por defecto de `ExtensionManager`. Gestiona la carga, inicialización, almacenamiento y descarga de extensiones (plugins) para pipelines, permitiendo la extensión dinámica de funcionalidades del sistema.

**Relaciones y por qué se usan:**
- **`ExtensionManager` (interfaz de dominio):** Implementa el contrato para la gestión de extensiones, facilitando la interoperabilidad y el reemplazo.
- **`PipelineExtension`:** Permite cargar y gestionar extensiones que implementan funcionalidades adicionales, siguiendo el principio de inversión de dependencias.
- **`ExtensionContext`, `StepDefinition`:** Proveen contexto y definición de pasos para inicializar y exponer nuevas capacidades.
- **`ServiceLoader`:** Permite el descubrimiento dinámico de implementaciones de extensiones en tiempo de ejecución, facilitando la modularidad.

---

## worker.application.worker.dsl.extensions.ExtensionBase
**Descripción ampliada:**
Clase base abstracta para todas las extensiones de pipeline. Proporciona métodos de inicialización y utilidades comunes, asegurando que todas las extensiones compartan una estructura y ciclo de vida coherentes.

**Relaciones y por qué se usan:**
- **`PipelineExtension`:** Define el contrato que deben cumplir todas las extensiones, permitiendo que el sistema las gestione de forma uniforme.
- **Subclases (`DockerExtension`, `GitExtension`, `NotificationExtension`):** Heredan de esta clase para reutilizar lógica común y facilitar la creación de nuevas extensiones.

---

## worker.application.worker.dsl.extensions.DockerExtension
**Descripción ampliada:**
Extensión que provee pasos para operaciones con contenedores Docker, como construir (`build`) y publicar (`push`) imágenes. Permite a los pipelines integrar flujos de trabajo de construcción y despliegue de contenedores de forma sencilla y reutilizable.

**Relaciones y por qué se usan:**
- **Hereda de `ExtensionBase`:** Reutiliza la lógica común y el ciclo de vida de las extensiones.
- **Define pasos (`StepDefinition`):** Expone operaciones Docker como pasos reutilizables en los pipelines.

---

## worker.application.worker.dsl.extensions.GitExtension
**Descripción ampliada:**
Extensión que provee pasos para operaciones con Git, como clonar repositorios (`checkout`) y crear etiquetas (`tag`). Facilita la integración de control de versiones en los pipelines de manera modular.

**Relaciones y por qué se usan:**
- **Hereda de `ExtensionBase`:** Reutiliza la estructura y utilidades comunes de las extensiones.
- **Define pasos (`StepDefinition`):** Permite que las operaciones Git sean integradas como pasos configurables.

---

## worker.application.worker.dsl.extensions.NotificationExtension
**Descripción ampliada:**
Extensión para enviar notificaciones, soportando servicios como Slack y correo electrónico. Permite a los pipelines informar de eventos relevantes a usuarios y sistemas externos.

**Relaciones y por qué se usan:**
- **Hereda de `ExtensionBase`:** Aprovecha la infraestructura común de inicialización y gestión de extensiones.
- **Define pasos (`StepDefinition`):** Expone servicios de notificación como pasos reutilizables.

---

## worker.application.worker.execution.DefaultExecutionStrategyManager
**Descripción ampliada:**
Implementación por defecto de `ExecutionStrategyManager`. Permite registrar, consultar y eliminar estrategias de ejecución (`JobExecutionStrategy`) asociadas a diferentes tipos de trabajo (`JobType`). Facilita la extensión y personalización de la lógica de ejecución según el tipo de trabajo.

**Relaciones y por qué se usan:**
- **`ExecutionStrategyManager` (interfaz de dominio):** Implementa el contrato para gestionar estrategias de ejecución, permitiendo el polimorfismo y la extensibilidad.
- **`JobExecutionStrategy` y `JobType`:** Gestiona distintas estrategias según el tipo de trabajo, promoviendo el principio de abierto/cerrado.

---

> _Esta documentación cubre las clases principales del submódulo `worker/application`, explicando tanto sus dependencias como el porqué de cada relación._
