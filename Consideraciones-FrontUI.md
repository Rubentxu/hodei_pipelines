# Consideraciones de Implementación Frontend y CLI - Kotlin Multiplatform (2024-2025)

Basado en las últimas actualizaciones y roadmap oficial de JetBrains, aquí están las consideraciones más actuales:

## 1. Cambios Críticos en el Ecosistema (2025)

### 1.1 Novedades Importantes
- **Compose Multiplatform para iOS es ahora Stable** y listo para producción
- **Compose Hot Reload** ya está disponible, permitiendo actualizaciones instantáneas de UI
- **Kotlin-to-Swift Export** tendrá su primera versión pública en 2025
- **Nuevo plugin KMP** para IntelliJ IDEA y Android Studio que simplifica toda la experiencia
- **Fleet discontinúa soporte KMP**: JetBrains se enfoca en IntelliJ IDEA y Android Studio

### 1.2 Stack Tecnológico Recomendado 2025
```toml
[versions]
kotlin = "2.0.20+"  # K2 compiler por defecto
compose-multiplatform = "1.7.3+"
ktor = "3.0.0+"
kotlinx-coroutines = "1.8.0+"
kotlinx-serialization = "1.7.0+"
clikt = "5.0.1"  # Para CLI
mordant = "3.0.0"  # Rich terminal output
```

## 2. Arquitectura Multiplatform Actualizada

### 2.1 Estructura de Proyecto Recomendada
```
project/
├── gradle/libs.versions.toml    # Version catalog centralizado
├── convention-plugins/           # Build logic compartida
│   └── src/main/kotlin/
│       ├── kmp-library.gradle.kts
│       ├── kmp-application.gradle.kts
│       └── compose-multiplatform.gradle.kts
│
├── shared/                      # Código compartido
│   ├── core/                   # Lógica de negocio pura
│   ├── data/                   # Repositorios, API clients
│   ├── ui-common/              # Componentes UI compartidos
│   └── resources/              # Nueva API de recursos
│
├── composeApp/                 # UI Compose Multiplatform
│   ├── src/
│   │   ├── commonMain/        # UI compartida
│   │   ├── desktopMain/       # Desktop específico
│   │   ├── wasmJsMain/        # Web con Wasm
│   │   └── iosMain/           # iOS específico
│   └── build.gradle.kts
│
├── desktopApp/                # Empaquetado desktop
├── webApp/                    # Configuración web
├── iosApp/                    # Proyecto Xcode
└── cli/                       # CLI Kotlin/Native
```


## 3. Compose Multiplatform - Mejores Prácticas 2025

### 3.1 Recursos Compartidos (Nueva API)
La nueva API de recursos permite incluir y acceder a más tipos de recursos en aplicaciones Compose Multiplatform:

```kotlin
// commonMain - Uso de la nueva API de recursos
import org.jetbrains.compose.resources.*

@Composable
fun Logo() {
    Image(
        painterResource(Res.drawable.logo),
        contentDescription = stringResource(Res.string.logo_description)
    )
}

// Soporte para variaciones por locale, densidad, tema
// resources/
//   drawable/
//   drawable-dark/
//   values/
//   values-es/
```

### 3.2 Preview Annotation Común
Fleet actualmente soporta la anotación @Preview para funciones @Composable sin parámetros:

```kotlin
// Usar compose.components.uiToolingPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun MyComponentPreview() {
    MyTheme {
        MyComponent()
    }
}
```

### 3.3 Performance Optimizations iOS
Con Kotlin 2.0.20, las optimizaciones de rendimiento para iOS muestran mejoras significativas: LazyGrid ~9% más rápido, VisualEffects 3.6x más rápido:

```kotlin
// Habilitar concurrent marking GC (experimental)
// En gradle.properties
kotlin.native.binary.gc=cms
kotlin.native.binary.gcMarkSingleThreaded=false
```

### 3.4 Drag and Drop Desktop
El mecanismo de drag-and-drop está implementado para desktop usando los modificadores dragAndDropSource y dragAndDropTarget:

```kotlin
@Composable
fun DraggableContent() {
    Box(
        modifier = Modifier
            .dragAndDropSource { /* configuración */ }
            .dragAndDropTarget { /* manejador */ }
    )
}
```

## 4. Web con Kotlin/Wasm

### 4.1 Optimizaciones Wasm
El archivo skiko.js es ahora redundante para aplicaciones Kotlin/Wasm, mejorando los tiempos de carga:

```html
<!-- Ya NO es necesario para Kotlin/Wasm -->
<!-- <script src="skiko.js"></script> -->

<!-- Solo necesario tu bundle -->
<script src="composeApp.js"></script>
```

### 4.2 Consideraciones Web
- **Compose for Web (Wasm)**: Para compartir UI con desktop/mobile
- **Compose HTML**: Para web-only con Kotlin/JS
- **Canvas vs DOM**: Compose Web usa Canvas, considerar implicaciones SEO

## 5. CLI con Kotlin/Native

### 5.1 Stack Recomendado
Clikt es la librería preferida para CLIs en Kotlin, sin reflexión y con soporte multiplataforma:

```kotlin
// build.gradle.kts
kotlin {
    // Para CLI nativo puro
    linuxX64 { binaries.executable() }
    macosX64 { binaries.executable() }
    macosArm64 { binaries.executable() }
    mingwX64 { binaries.executable() }
    
    // Alternativa: JVM target para usar con GraalVM
    jvm { 
        compilations.all {
            kotlinOptions.jvmTarget = "21"
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:5.0.1")
                implementation("com.github.ajalt.mordant:mordant:3.0.0")
                implementation("com.github.ajalt.colormath:colormath:3.5.0")
            }
        }
    }
}
```

### 5.2 Consideraciones Native vs GraalVM
Para CLIs, considerar tanto Kotlin/Native como GraalVM según necesidades:

**Kotlin/Native:**
- ✅ Binarios más pequeños (~4MB)
- ✅ Sin dependencias de runtime
- ✅ Inicio instantáneo
- ❌ Compilación más lenta
- ❌ Menos librerías disponibles

**GraalVM Native Image:**
- ✅ Acceso a todo el ecosistema JVM
- ✅ Mejor tooling de profiling
- ❌ Binarios más grandes (~12-20MB)
- ❌ Configuración más compleja
- ❌ Limitaciones con reflexión

### 5.3 CLI Architecture Pattern
```kotlin
// Estructura modular recomendada
sealed interface Command {
    data object Version : Command
    
    @Serializable
    data class Config(
        val verbose: Boolean = false,
        val format: OutputFormat = OutputFormat.Table
    ) : Command
    
    sealed interface JobCommand : Command {
        data class Create(
            val template: String,
            val parameters: Map<String, String>
        ) : JobCommand
        
        data class List(
            val filters: JobFilters,
            val limit: Int = 50
        ) : JobCommand
    }
}

// Implementación con Clikt
class OrchestrateCli : CliktCommand() {
    override fun run() = Unit
}

class JobCommands : CliktCommand(name = "job") {
    override fun run() = Unit
}

class CreateJob : CliktCommand(name = "create") {
    val template by option("-t", "--template").required()
    val params by option("-p", "--param").associate()
    
    override fun run() {
        // Implementación
    }
}
```

## 6. Arquitectura Compartida

### 6.1 ViewModel Multiplataforma
```kotlin
// commonMain
expect abstract class ViewModel() {
    protected open fun onCleared()
}

// androidMain
actual abstract class ViewModel : androidx.lifecycle.ViewModel()

// desktopMain/iosMain
actual abstract class ViewModel {
    actual open fun onCleared() {}
}
```

### 6.2 Navigation Type-Safe
Compose Multiplatform 1.7.3 incluye soporte para Type-safe Navigation:

```kotlin
@Serializable
sealed interface Screen {
    @Serializable
    data object Home : Screen
    
    @Serializable
    data class JobDetail(val jobId: String) : Screen
}

@Composable
fun Navigation() {
    val navigator = rememberNavigator<Screen>(Screen.Home)
    
    NavHost(navigator) {
        scene<Screen.Home> { HomeScreen() }
        scene<Screen.JobDetail> { JobDetailScreen(it.jobId) }
    }
}
```

## 7. Testing Multiplatform

### 7.1 UI Testing
Compose Multiplatform 1.6.0 introduce una API de UI testing:

```kotlin
// commonTest
class UITest {
    @Test
    fun testButton() = runComposeUiTest {
        setContent {
            MyButton()
        }
        
        onNodeWithText("Click me").performClick()
        onNodeWithText("Clicked!").assertExists()
    }
}
```

### 7.2 Configuración de Testing
```kotlin
kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.compose.ui:ui-test-junit4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }
    }
}
```

## 8. Distribución

### 8.1 Desktop con Conveyor
```kotlin
// Configuración para Conveyor
conveyor {
    appName = "Orchestrator"
    vendor = "YourCompany"
    
    linux {
        packages = listOf("deb", "rpm", "appimage")
    }
    
    mac {
        sign = true
        notarize = true
    }
    
    windows {
        sign = true
        msi = true
    }
}
```

### 8.2 CLI Distribution
- **Homebrew**: Fórmula para macOS/Linux
- **Scoop**: Manifest para Windows
- **GitHub Releases**: CI/CD con matrix builds
- **Container**: Distroless para seguridad

## 9. Consideraciones de Performance

### 9.1 Optimizaciones Compose
- Usar `remember` y `derivedStateOf` apropiadamente
- `@Stable` y `@Immutable` para optimizar recomposiciones
- Lazy layouts para listas grandes
- Instrumentación con Layout Inspector

### 9.2 Binary Size Optimization
```kotlin
// Para Kotlin/Native
kotlin {
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf(
                    "-opt",
                    "-Xbinary=stripDebugInfoFromReleaseShared=true"
                )
            }
        }
    }
}
```

## 10. Herramientas y DevEx

### 10.1 IDE Support
- Usar el nuevo plugin KMP para IntelliJ IDEA/Android Studio
- Live Templates para Compose
- File templates para screens/components
- Custom inspections para anti-patterns

### 10.2 CI/CD Pipeline
```yaml
# GitHub Actions ejemplo
strategy:
  matrix:
    os: [ubuntu-latest, macos-latest, windows-latest]
    include:
      - os: ubuntu-latest
        cmd: ./gradlew :desktop:packageDeb
      - os: macos-latest
        cmd: ./gradlew :desktop:packageDmg
      - os: windows-latest
        cmd: ./gradlew :desktop:packageMsi
```

Estas consideraciones reflejan el estado actual del ecosistema Kotlin Multiplatform para desarrollo frontend y CLI, incorporando las últimas mejoras y mejores prácticas de 2024-2025.