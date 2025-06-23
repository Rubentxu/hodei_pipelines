# Documentación de Clases – worker/domain

Este documento describe cada clase e interfaz encontrada en el submódulo `worker/domain`, su propósito, contexto y relaciones con otras clases del sistema, explicando también por qué utiliza cada dependencia.

---

## worker.domain.worker.model.dsl

### StepDefinition
**Descripción ampliada:** Define un paso (step) de pipeline, incluyendo su nombre, descripción, parámetros y el ejecutor asociado. Permite modelar y ejecutar operaciones atómicas y reutilizables dentro del pipeline.

**Relaciones y por qué se usan:**
- **`ParameterDefinition`, `ParameterType`:** Permiten definir los parámetros necesarios para cada paso, asegurando validación y tipado correcto.
- **`StepExecutionContext`:** Proporciona el contexto necesario para ejecutar el paso, incluyendo variables de entorno y parámetros.
- **`StepResult`:** Permite modelar el resultado de la ejecución del paso, facilitando la gestión de errores y estados.

---

### ParameterDefinition
**Descripción ampliada:** Especifica las características de un parámetro de paso, como nombre, tipo, si es requerido, valor por defecto y validación. Asegura que cada paso reciba la información necesaria de forma segura y tipada.

**Relaciones y por qué se usan:**
- **`ParameterType`:** Define el tipo de dato del parámetro, facilitando la validación y conversión.

---

### ParameterType
**Descripción ampliada:** Enumera los tipos de parámetros posibles en un paso (STRING, BOOLEAN, FILE, etc). Permite la validación y el manejo adecuado de los datos de entrada en los pasos.

---

### StepExecutionContext
**Descripción ampliada:** Contexto de ejecución de un paso, incluyendo pipeline, nombre del paso, parámetros y entorno. Facilita la ejecución aislada y contextualizada de cada operación dentro del pipeline.

---

### StepResult
**Descripción ampliada:** Resultado de la ejecución de un paso (éxito, fallo, inestable). Permite comunicar el estado y los resultados de cada paso al sistema de orquestación.

---

### ExtensionContext
**Descripción ampliada:** Contexto para la inicialización de extensiones, permitiendo almacenar y recuperar propiedades. Facilita la personalización y el almacenamiento de información específica de cada extensión.

---

### Excepciones de extensiones
**Descripción ampliada:** Excepciones específicas para errores en la carga o inicialización de extensiones. Permiten un manejo robusto y explícito de fallos en la extensión del sistema.

---

### LibraryManager (interfaz)
**Descripción ampliada:** Interfaz para la gestión de librerías externas: carga, registro, descarga y consulta de librerías. Permite desacoplar la lógica de gestión de dependencias del resto del sistema.

**Relaciones y por qué se usan:**
- **`LibraryReference`, `LibraryDefinition`:** Permiten modelar las librerías y su ciclo de vida.

---

### PipelineContext
**Descripción ampliada:** Contexto avanzado de ejecución de pipeline, con capacidades DSL. Permite el manejo de etapas, agentes, herramientas, parámetros, SCM y artefactos. Expone subcontextos como `EnvironmentContext`, `AgentContext`, `ToolsContext`, `SCMContext`, y `BuildContext` para facilitar la construcción de pipelines complejos y reutilizables.

**Relaciones y por qué se usan:**
- **Subcontextos (`EnvironmentContext`, `AgentContext`, etc.):** Permiten organizar y encapsular funcionalidades específicas, promoviendo la claridad y reutilización.
- **`LibraryManager`, `PipelineSecurityManager`:** Permiten gestionar dependencias y seguridad de forma centralizada y coherente.

---

## worker.domain.worker.model.execution

### JobExecutionResult
**Descripción ampliada:** Resultado de la ejecución de un trabajo. Incluye código de salida, estado, métricas, salida estándar y mensaje de error. Proporciona métodos de fábrica para éxito y fallo, facilitando la creación y manejo de resultados de ejecución.

**Relaciones y por qué se usan:**
- **`JobStatus` (del dominio):** Permite representar el estado del trabajo de forma tipada y coherente con el resto del sistema.

---

## worker.domain.worker.model.library

### LibraryDefinition
**Descripción ampliada:** Define una librería con identificador, versión, archivos JAR, dependencias, metadatos, permisos y sumas de verificación. Permite modelar de forma rica y segura las dependencias externas.

**Relaciones y por qué se usan:**
- **`LibraryMetadata`, `Permission`:** Permiten describir y controlar el acceso y uso de la librería.

---

### LibraryMetadata
**Descripción ampliada:** Metadatos descriptivos de una librería (nombre, autor, licencia, etc). Facilita la gestión y búsqueda de librerías.

---

### Permission
**Descripción ampliada:** Enumera los permisos que puede requerir una librería (acceso a red, sistema de archivos, etc). Permite controlar y restringir el comportamiento de las dependencias externas.

---

### LibraryReference
**Descripción ampliada:** Referencia a una librería cargada, con acceso a instancias de clases y comprobación de clases disponibles. Facilita la reutilización y el aislamiento de dependencias en tiempo de ejecución.

---

### Excepciones de librerías
**Descripción ampliada:** Excepciones específicas para errores de librerías (no encontrada, descarga, verificación, carga). Permiten un manejo robusto de fallos en la gestión de dependencias.

---

## worker.domain.worker.model.security

### SecurityCheckResult
**Descripción ampliada:** Resultado de una comprobación de seguridad: permitida o denegada (con violaciones). Permite controlar el acceso y la ejecución de recursos potencialmente peligrosos.

---

### SecurityPolicy
**Descripción ampliada:** Política de seguridad configurable para la ejecución de pipelines (acceso a recursos, límites de memoria, sandbox, etc). Permite adaptar el nivel de seguridad según el contexto de ejecución.

---

### SecurityViolation
**Descripción ampliada:** Representa una violación de seguridad detectada durante la ejecución (patrones peligrosos, acceso no autorizado, etc). Facilita el diagnóstico y la auditoría de incidentes de seguridad.

---

## worker.domain.worker.ports

### ExecutionStrategyManager (interfaz)
**Descripción ampliada:** Interfaz para la gestión de estrategias de ejecución de trabajos según el tipo de trabajo. Permite desacoplar la lógica de selección y ejecución de estrategias.

**Relaciones y por qué se usan:**
- **`JobExecutionStrategy`, `JobType`:** Permiten registrar y seleccionar estrategias específicas para cada tipo de trabajo.

---

### ExtensionManager (interfaz)
**Descripción ampliada:** Interfaz para la gestión de extensiones de pipeline: carga, registro, consulta y descarga. Permite extender dinámicamente las capacidades del sistema.

**Relaciones y por qué se usan:**
- **`PipelineExtension`, `StepDefinition`:** Permiten gestionar y exponer nuevas funcionalidades y pasos en el pipeline.

---

### JobExecutionStrategy (interfaz)
**Descripción ampliada:** Interfaz para la estrategia de ejecución de trabajos, incluyendo ejecución y consulta de tipos soportados. Permite definir diferentes formas de ejecutar trabajos según sus características.

---

### LibraryRepository (interfaz)
**Descripción ampliada:** Interfaz para el repositorio de librerías: obtención, guardado, borrado y búsqueda de librerías. Permite desacoplar el almacenamiento de la lógica de negocio.

**Relaciones y por qué se usan:**
- **`LibraryDefinition`:** Permite gestionar de forma robusta las entidades de librería.

---

### PipelineExtension (interfaz)
**Descripción ampliada:** Interfaz base para extensiones de pipeline, define métodos de inicialización, pasos y variables globales. Permite la integración y gestión uniforme de extensiones.

---

### PipelineSecurityManager (interfaz)
**Descripción ampliada:** Interfaz para la gestión de seguridad durante la ejecución de pipelines: comprobación de accesos y política de seguridad. Permite centralizar y personalizar los controles de seguridad.

**Relaciones y por qué se usan:**
- **`SecurityPolicy`, `SecurityCheckResult`, `FileOperation`:** Permiten definir y aplicar políticas y controles de acceso de forma granular.

---

> _Esta documentación cubre las clases e interfaces principales del submódulo `worker/domain`, explicando tanto sus dependencias como el porqué de cada relación._
