# Guía de Desarrollo de Extensiones

Esta guía te muestra cómo crear extensiones de steps personalizados para Hodei Pipelines usando librerías de terceros.

## Creando una Extensión

### 1. Estructura del Proyecto

```
my-extension/
├── build.gradle.kts
├── src/main/kotlin/
│   └── com/mycompany/
│       └── MyStepExtension.kt
└── src/main/resources/
    └── META-INF/services/
        └── dev.rubentxu.hodei.pipelines.dsl.extensions.StepExtension
```

### 2. Implementar StepExtension

```kotlin
package com.mycompany

import dev.rubentxu.hodei.pipelines.dsl.extensions.*
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory

class MyStepExtension : BaseStepExtension() {
    override val name: String = "myStep"
    override val version: String = "1.0.0"
    override val category: StepCategory = StepCategory.CUSTOM
    override val description: String = "My custom step"
    
    override val dependencies: List<Dependency> = listOf(
        Dependency("org.apache.httpcomponents", "httpclient", "4.5.14")
    )
    
    override fun createExecutor(): StepExecutor = MyStepExecutor()
    
    override fun registerDslFunctions(builder: StepsBuilder) {
        // Registrar funciones DSL aquí
    }
}
```

### 3. Implementar StepExecutor

```kotlin
class MyStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is ExtensionStep) { "Expected ExtensionStep" }
        
        // Tu lógica aquí usando librerías de terceros
        context.println("Executing my custom step...")
        
        // Ejemplo usando HttpClient
        val httpClient = HttpClients.createDefault()
        // ... usar la librería
    }
}
```

### 4. Registrar en ServiceLoader

Crea el archivo `META-INF/services/dev.rubentxu.hodei.pipelines.dsl.extensions.StepExtension`:

```
com.mycompany.MyStepExtension
```

### 5. Configurar build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    implementation("dev.rubentxu.hodei:pipeline-dsl-core:1.0.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    // Otras dependencias de terceros
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.mycompany"
            artifactId = "my-step-extension"
            version = "1.0.0"
        }
    }
}
```

## Uso de la Extensión

### 1. Instalación Automática

```kotlin
// En tu pipeline o configuración
val extensionRegistry = ExtensionRegistry(stepExecutorRegistry, libraryManager)

// Instalar desde repositorio Maven
extensionRegistry.installExtension(
    groupId = "com.mycompany",
    artifactId = "my-step-extension", 
    version = "1.0.0"
)
```

### 2. Carga Manual

```kotlin
// Cargar desde JAR local
extensionRegistry.loadExtensionsFromJar(File("extensions/my-extension.jar"))

// Cargar desde directorio
extensionRegistry.loadExtensionsFromDirectory(File("extensions/"))

// Auto-cargar desde ubicaciones estándar
extensionRegistry.autoLoadExtensions()
```

### 3. Uso en Pipeline DSL

```kotlin
pipeline("example") {
    stages {
        stage("custom") {
            steps {
                // Tu step personalizado estará disponible
                custom("myStep", mapOf(
                    "url" to "https://api.example.com",
                    "token" to "secret"
                ))
            }
        }
    }
}
```

## Ejemplos Avanzados

### Extensión con DSL Personalizado

```kotlin
class AdvancedStepExtension : BaseStepExtension() {
    override fun registerDslFunctions(builder: StepsBuilder) {
        // Esto requeriría bytecode manipulation o reflection avanzada
        // Para crear sintaxis como:
        // 
        // slack("Hello!", "#general")
        // 
        // docker {
        //     image = "node:18"
        //     run = "npm test"
        // }
    }
}
```

### Extensión con Validación

```kotlin
override fun validate(step: Step): List<String> {
    val errors = mutableListOf<String>()
    
    if (step !is ExtensionStep) {
        errors.add("Invalid step type")
        return errors
    }
    
    val params = step.parameters
    
    // Validar parámetros requeridos
    if (params["apiKey"] == null) {
        errors.add("apiKey is required")
    }
    
    // Validar formato
    val url = params["url"]?.toString()
    if (url != null && !url.startsWith("https://")) {
        errors.add("URL must use HTTPS")
    }
    
    return errors
}
```

### Extensión con Configuración Compleja

```kotlin
@Serializable
data class MyStepConfig(
    val endpoint: String,
    val authentication: AuthConfig,
    val retries: Int = 3,
    val timeout: Duration = Duration.seconds(30)
)

@Serializable
sealed class AuthConfig {
    @Serializable
    data class ApiKey(val key: String) : AuthConfig()
    
    @Serializable  
    data class OAuth(val token: String) : AuthConfig()
}
```

## Mejores Prácticas

### 1. Gestión de Dependencias
- Declara todas las dependencias explícitamente
- Usa versiones estables
- Evita conflictos con el core del sistema

### 2. Manejo de Errores
- Implementa validación robusta
- Maneja excepciones gracefully
- Proporciona mensajes de error útiles

### 3. Documentación
- Documenta todos los parámetros
- Proporciona ejemplos de uso
- Incluye información de compatibilidad

### 4. Testing
```kotlin
class MyStepExtensionTest {
    @Test
    fun `should validate required parameters`() {
        val extension = MyStepExtension()
        val step = ExtensionStep("myStep", "action", emptyMap())
        
        val errors = extension.validate(step)
        
        assertThat(errors).contains("apiKey is required")
    }
}
```

### 5. Versionado Semántico
- Usa semantic versioning (X.Y.Z)
- Documenta breaking changes
- Mantén compatibilidad hacia atrás cuando sea posible

## Distribución

### 1. Maven Central
```bash
./gradlew publishToMavenCentral
```

### 2. Repositorio Privado
```kotlin
publishing {
    repositories {
        maven {
            url = uri("https://my-company.com/maven")
            credentials {
                username = project.findProperty("repo.username") as String?
                password = project.findProperty("repo.password") as String?
            }
        }
    }
}
```

### 3. Distribución Local
```bash
./gradlew publishToMavenLocal
```

Con este sistema, los developers pueden fácilmente extender Hodei Pipelines con nuevas funcionalidades usando cualquier librería de terceros, manteniendo la integración perfecta con el DSL existente.