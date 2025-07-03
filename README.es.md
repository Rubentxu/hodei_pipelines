# Hodei-Pipelines: Orquestador de Pipelines Distribuidos

**Hodei-Pipelines** es un sistema moderno, distribuido y escalable para orquestar y ejecutar pipelines de trabajos. Construido con Kotlin y gRPC, aprovecha una arquitectura hexagonal limpia para garantizar la mantenibilidad, la capacidad de prueba y la separación de conceptos.

## ✨ Características Clave

- **Ejecución Distribuida de Trabajos**: Ejecuta trabajos (scripts o comandos) en un pool de workers escalables.
- **Patrón de Estrategia para la Ejecución de Trabajos**: Múltiples estrategias de ejecución, incluyendo scripting de Kotlin, compilador integrable y comandos del sistema.
- **DSL de Pipeline**: DSL de Pipeline similar a Jenkins para definir pipelines de construcción complejos con etapas, pasos y ejecución en paralelo.
- **Sandbox de Seguridad**: Políticas de seguridad configurables con detección y prevención de código peligroso.
- **Gestión de Bibliotecas**: Carga dinámica de JARs y gestión de dependencias para extensiones de pipeline.
- **Sistema de Extensiones**: Soporte para plugins de terceros para ampliar la funcionalidad del pipeline.
- **Streaming de Eventos**: Eventos de pipeline en tiempo real a través de gRPC para monitorización e integración.
- **Gestión de Artefactos**: Capacidades avanzadas de almacenamiento en caché, compresión y transferencia de artefactos.
- **Arquitectura Hexagonal**: Una separación limpia entre la lógica de dominio central y los detalles de la infraestructura (por ejemplo, bases de datos, protocolos de red).
- **Comunicación basada en gRPC**: Comunicación eficiente y fuertemente tipada entre el servidor central y los workers mediante Protocol Buffers.
- **Pools de Workers Dinámicos**: Gestiona y escala pools de workers según políticas configurables.
- **Programación Avanzada de Trabajos**: Estrategias de programación sofisticadas para asignar trabajos a los workers más adecuados.
- **Escalado Automático de Workers**: Políticas para escalar automáticamente los recursos de los workers hacia arriba o hacia abajo según la demanda, inspirado en Kubernetes.

## 🏛️ Descripción General de la Arquitectura

El proyecto sigue una estricta **Arquitectura Hexagonal (Puertos y Adaptadores)**. Esto aísla la lógica de negocio central de las preocupaciones externas.

- **`core`**: Contiene el corazón de la aplicación.
  - **`domain`**: Define las entidades de negocio, las reglas y los importantes **puertos** (interfaces) que el dominio necesita para funcionar.
  - **`application`**: Implementa los casos de uso que orquestan la lógica del dominio.
  - **`infrastructure`**: Proporciona implementaciones en memoria de los puertos para pruebas y operación independiente.
- **`backend`**: El componente del servidor central. Contiene adaptadores gRPC que exponen los casos de uso de la aplicación a la red.
- **`worker`**: El componente cliente que se registra en el servidor, recibe trabajos, los ejecuta e informa de los resultados.

Para una inmersión profunda en la arquitectura, los diagramas de componentes y el modelo de dominio, consulta el [**Documento de Patrones del Sistema**](./docs/systemPatterns.md).

## 🔧 Características del DSL de Pipeline

### Ejecución de Scripts de Kotlin
Ejecuta scripts de Kotlin con acceso completo al DSL de Pipeline:

```kotlin
pipeline {
    stage("Build") {
        script {
            println("Construyendo el proyecto...")
            sh("./gradlew build")
        }
    }
    stage("Test") {
        parallel {
            task("Pruebas Unitarias") {
                sh("./gradlew test")
            }
            task("Pruebas de Integración") {
                sh("./gradlew integrationTest")
            }
        }
    }
}
```

### Estrategias de Ejecución
- **KotlinScriptingStrategy**: Ejecuta scripts de Kotlin utilizando la API de Scripting de Kotlin.
- **CompilerEmbeddableStrategy**: Compila y ejecuta código Kotlin utilizando kotlin-compiler-embeddable.
- **SystemCommandStrategy**: Ejecuta comandos del sistema y scripts de shell.

### Características de Seguridad
- **Detección de Código Peligroso**: Detecta y previene automáticamente la ejecución de patrones de código potencialmente dañinos.
- **Políticas de Seguridad Configurables**: Control detallado sobre las operaciones permitidas.
- **Ejecución en Sandbox**: Entorno de ejecución aislado para scripts.

### Gestión de Bibliotecas
- **Carga Dinámica de JARs**: Carga y gestiona dependencias JAR externas en tiempo de ejecución.
- **Resolución de Conflictos de Versiones**: Maneja los conflictos de dependencias automáticamente.
- **Carga de Extensiones**: Soporte para extensiones y plugins de terceros.

## 🛠️ Pila Tecnológica

- **Lenguaje**: [Kotlin](https://kotlinlang.org/) con Coroutines para programación asíncrona.
- **Ejecución de Scripts**: API de Scripting de Kotlin y kotlin-compiler-embeddable para ejecución dinámica de código.
- **Comunicación**: [gRPC](https://grpc.io/) con [Protocol Buffers](https://developers.google.com/protocol-buffers) para RPC de alto rendimiento.
- **Sistema de Construcción**: [Gradle](https://gradle.org/) con el DSL de Kotlin.
- **Pruebas**: JUnit 5, Mockito y servidores gRPC embebidos para pruebas de integración completas.
- **Logging**: [KotlinLogging](https://github.com/MicroUtils/kotlin-logging).
- **Seguridad**: Sandbox de seguridad personalizado con políticas configurables.
- **Compresión**: Soporte de GZIP para la optimización de la transferencia de artefactos.

Para más detalles sobre la tecnología y las herramientas, consulta el [**Documento de Contexto Técnico**](./docs/techContext.md).

## 🚀 Cómo Empezar

### Prerrequisitos

- JDK 17 o superior.
- Gradle.

### Compilación

Para compilar todo el proyecto y ejecutar todas las comprobaciones, ejecuta el siguiente comando desde el directorio raíz:

```bash
./gradlew build
```

### Ejecución

1.  **Iniciar el Servidor**: Ejecuta la función `main` en `backend/application/src/main/kotlin/dev/rubentxu/hodei/pipelines/application/HodeiPipelinesServer.kt`.
2.  **Iniciar un Worker**: Ejecuta la función `main` en `worker/application/src/main/kotlin/dev/rubentxu/hodei/pipelines/worker/application/PipelineWorkerApp.kt`.

## 🧪 Probando el DSL de Pipeline

### Ejecutando la Suite de Pruebas

Ejecuta todas las pruebas para verificar la funcionalidad:

```bash
# Ejecutar todas las pruebas
./gradlew test

# Ejecutar solo las pruebas del worker
./gradlew :worker:infrastructure:test

# Ejecutar pruebas con salida detallada
./gradlew test --info
```

### Pruebas Manuales con Ejemplos

#### 1. Ejemplo de Pipeline Básico

Crea un archivo de script de Kotlin simple `test-pipeline.kts`:

```kotlin
pipeline {
    stage("Hola Mundo") {
        script {
            println("¡Hola desde Hodei-Pipelines!")
            sh("echo 'Información del sistema:'")
            sh("uname -a")
        }
    }
}
```

#### 2. Pipeline de Múltiples Etapas

```kotlin
pipeline {
    stage("Preparación") {
        script {
            println("Configurando el entorno...")
            setEnv("BUILD_NUMBER", "123")
            setEnv("PROJECT_NAME", "hodei-pipelines")
        }
    }
    
    stage("Compilación") {
        parallel {
            task("Compilar") {
                println("Compilando fuentes...")
                sh("echo 'Compilando...'")
            }
            task("Recursos") {
                println("Procesando recursos...")
                sh("echo 'Procesando recursos...'")
            }
        }
    }
    
    stage("Prueba") {
        script {
            println("Ejecutando pruebas para el proyecto: ${env("PROJECT_NAME")}")
            println("Número de compilación: ${env("BUILD_NUMBER")}")
            sh("echo '¡Todas las pruebas pasaron!'")
        }
    }
}
```

#### 3. Pipeline de Manejo de Errores

```kotlin
pipeline {
    stage("Operaciones Seguras") {
        try {
            script {
                println("Realizando operaciones seguras...")
                sh("echo 'Esto tendrá éxito'")
            }
        } catch (e: Exception) {
            println("Error inesperado: ${e.message}")
        }
    }
    
    stage("Demostración de Error") {
        try {
            script {
                // Esto será bloqueado por seguridad
                System.exit(1)
            }
        } catch (e: SecurityException) {
            println("La política de seguridad evitó una operación peligrosa: ${e.message}")
        }
    }
}
```

### Pruebas de Integración

El proyecto incluye pruebas de integración completas que demuestran el uso real:

#### Ejecutando Pruebas de Integración

```bash
# Ejecutar prueba de integración específica
./gradlew :worker:infrastructure:test --tests "*MinimalIntegrationTest*"

# Ejecutar pruebas de registro de workers
./gradlew :worker:infrastructure:test --tests "*WorkerRegistrationIntegrationTest*"

# Ejecutar pruebas de estrategia de ejecución
./gradlew :worker:infrastructure:test --tests "*JobExecutionStrategyTest*"
```

#### Informe de Cobertura de Pruebas

Genera informes de cobertura de pruebas:

```bash
./gradlew test jacocoTestReport
open worker/infrastructure/build/reports/jacoco/test/html/index.html
```

### Probando Diferentes Estrategias de Ejecución

#### 1. Prueba de KotlinScriptingStrategy

```kotlin
// Esto se prueba automáticamente, pero puedes verlo en:
// worker/infrastructure/src/test/kotlin/.../execution/JobExecutionStrategyTest.kt

@Test
fun `debería ejecutar un script de Kotlin usando KotlinScriptingStrategy`() = runTest {
    val script = '''
        pipeline {
            stage("Prueba de Script de Kotlin") {
                script {
                    println("Probando la Estrategia de Scripting de Kotlin")
                    val result = (1..5).sum()
                    println("Suma de 1-5: ${'$'}result")
                }
            }
        }
    '''.trimIndent()
    
    // Ejecución de la prueba...
}
```

#### 2. Pruebas de Políticas de Seguridad

```kotlin
@Test  
fun `debería bloquear patrones de código peligrosos`() = runTest {
    val dangerousScript = '''
        pipeline {
            stage("Operaciones Peligrosas") {
                script {
                    System.exit(1) // Esto debería ser bloqueado
                }
            }
        }
    '''.trimIndent()
    
    // Verificar que se lanza una excepción de seguridad...
}
```

### Pruebas de Rendimiento

#### Benchmark de Ejecución de Pipeline

```bash
# Ejecutar pruebas de rendimiento
./gradlew :worker:infrastructure:test --tests "*PerformanceTest*"

# Probar con scripts más grandes
./gradlew :worker:infrastructure:test -Dtest.script.size=large
```

#### Pruebas de Uso de Memoria

```kotlin
// Monitorear el uso de memoria durante la ejecución del pipeline
pipeline {
    stage("Prueba de Memoria") {
        script {
            val runtime = Runtime.getRuntime()
            println("Memoria usada: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB")
            
            // Crear algunos objetos para probar la memoria
            val largeList = (1..10000).toList()
            println("Lista creada con ${largeList.size} elementos")
        }
    }
}
```

### Depuración y Solución de Problemas

#### Habilitar Logging de Depuración

Añade a tu configuración de prueba:

```kotlin
// En la configuración de la prueba
System.setProperty("kotlin.script.classpath", System.getProperty("java.class.path"))
System.setProperty("logging.level.dev.rubentxu.hodei.pipelines", "DEBUG")
```

#### Probar el Streaming de Eventos

```kotlin
@Test
fun `debería emitir eventos de pipeline`() = runTest {
    val events = mutableListOf<JobExecutionEvent>()
    
    // Recolectar eventos durante la ejecución
    executor.execute(job, workerId).collect { event ->
        events.add(event)
        println("Evento: ${event::class.simpleName}")
    }
    
    // Verificar que se emitieron los eventos
    assertThat(events).hasSize(expectedEventCount)
}
```

### Pruebas de Carga

#### Múltiples Workers

```bash
# Iniciar múltiples workers para pruebas de carga
./gradlew :worker:infrastructure:test --tests "*MultipleWorkersTest*"
```

#### Ejecución Concurrente de Pipelines

```kotlin
@Test
fun `debería manejar la ejecución concurrente de pipelines`() = runTest {
    val workers = (1..5).map { createWorker("worker-$it") }
    val jobs = (1..10).map { createTestJob("job-$it") }
    
    // Ejecutar trabajos concurrentemente
    workers.forEach { it.start() }
    // Enviar trabajos y verificar la ejecución
}
```

### Monitoreo de Resultados de Pruebas

#### Salida de Pruebas en Tiempo Real

```bash
# Observar la ejecución de pruebas en tiempo real
./gradlew test --continuous

# Ejecutar pruebas con el demonio de gradle para una ejecución más rápida
./gradlew test --daemon
```

#### Informes de Pruebas

Después de ejecutar las pruebas, visualiza los informes detallados:

```bash
# Abrir el informe de pruebas en el navegador (Linux/macOS)
open worker/infrastructure/build/reports/tests/test/index.html

# O revisa la salida del terminal para la ruta directa del archivo
```

### Escenarios de Prueba Personalizados

Crea tus propios escenarios de prueba extendiendo la infraestructura de pruebas existente:

```kotlin
class CustomPipelineTest : IntegrationTestBase() {
    
    @Test
    fun `debería ejecutar un escenario de pipeline personalizado`() = runTest {
        val customScript = '''
            pipeline {
                // Tu lógica de pipeline personalizada aquí
            }
        '''.trimIndent()
        
        val result = executeScript(customScript)
        // Afirma tus expectativas
    }
}
```

Este enfoque integral de pruebas asegura que todas las características del DSL de Pipeline funcionen correctamente y proporciona ejemplos para que los usuarios entiendan las capacidades del sistema.

## 📚 Documentación Detallada

Este proyecto utiliza un "Registro de Conocimiento" para mantener una documentación exhaustiva. Toda la documentación detallada se encuentra en el directorio `/docs`.

- **[Resumen del Proyecto](./docs/projectbrief.md)**: Objetivos y requisitos de alto nivel.
- **[Contexto del Producto](./docs/productContext.md)**: El "porqué" detrás del proyecto y los objetivos de la experiencia del usuario.
- **[Patrones del Sistema](./docs/systemPatterns.md)**: Arquitectura detallada, diagramas y patrones de diseño.
- **[Guía del DSL de Pipeline](./docs/pipeline-dsl-guide.md)**: Guía completa de las características y el uso del DSL de Pipeline.
- **[Estructura del Proyecto](./docs/project_structure.md)**: Un desglose completo de todos los módulos y directorios clave.
- **[Contexto Técnico](./docs/techContext.md)**: Detalles sobre la pila tecnológica y las herramientas de desarrollo.
- **[Contexto Activo](./docs/activeContext.md)**: Foco de trabajo actual, próximos pasos y decisiones activas.

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Consulta el archivo `CONTRIBUTING.md` para ver las directrices. (Nota: Este archivo es un marcador de posición).

## 📄 Licencia

Este proyecto está licenciado bajo la Licencia MIT. Consulta el archivo `LICENSE` para más detalles. (Nota: Este archivo es un marcador de posición).

---

Analiza el siguiente fragmento de código del proyecto. Basándote en tu rol como experto en Connascence, realiza un informe completo.

Tu informe debe incluir:
- **Resumen de Acoplamiento:** Una visión general del nivel de acoplamiento que percibes en el código.
- **Detección de Connascence por Tipo:**
  - **Connascence de Nombre/Tipo:** ¿Hay clases con demasiadas dependencias (alto Fan-Out o CBO)?
  - **Connascence de Posición:** ¿Existen métodos con listas de parámetros largas o "Data Clumps" que podrían ser extraídos a su propia clase?
  - **Connascence de Algoritmo:** ¿Hay indicios de lógica duplicada que debería ser centralizada?
  - **Connascence de Significado/Convención:** ¿Detectas "números mágicos" o "strings mágicos" cuyo significado es implícito?
- **Sugerencias de Refactorización:** Para los puntos más problemáticos, propón cambios específicos en el código para mejorar el diseño y reducir el acoplamiento. Explica el "antes" y el "después" en términos de tipos de connascence.

A partir de tu informe, proporciona sugerencias de refactorización para mejorar el acoplamiento y el disenño del código. 
Como paso final, idea un plan de refactorización para mejorar el acoplamiento y el diseño del código.