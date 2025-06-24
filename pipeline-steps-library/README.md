# Hodei Pipeline Steps Library

Una librería completa de steps para pipelines compatible con Jenkins Pipeline DSL, implementada en Kotlin con DSL natural y type-safe.

## 🚀 Características

- **100% Compatible con Jenkins**: Mismas firmas y comportamiento que Jenkins Pipeline DSL
- **DSL Type-Safe**: Aprovecha el sistema de tipos de Kotlin para detectar errores en compile-time
- **Extensible**: Sistema de plugins para agregar steps personalizados
- **Performante**: Implementación optimizada con coroutines para operaciones concurrentes
- **Completa**: Incluye workflow-basic-steps y pipeline-utility-steps

## 📦 Instalación

### Gradle

```kotlin
dependencies {
    implementation("dev.rubentxu.hodei:pipeline-steps-library:1.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>dev.rubentxu.hodei</groupId>
    <artifactId>pipeline-steps-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 🔧 Uso Básico

```kotlin
import dev.rubentxu.hodei.pipelines.steps.dsl.*

pipeline("example") {
    stages {
        stage("build") {
            steps {
                // Workflow basic steps
                echo("Hello, World!")
                sh("echo 'Building application...'")
                
                // File operations
                writeFile("config.json", """{"version": "1.0.0"}""")
                val content = readFile("config.json")
                
                // Utility steps
                val jsonData = readJSON("config.json")
                writeJSON("output.json", mapOf("result" to "success"))
                
                // Archive operations
                zip("artifacts.zip", "**/*.json")
                val files = findFiles("**/*.zip")
            }
        }
    }
}
```

## 📋 Steps Disponibles

### Workflow Basic Steps

Equivalentes a [Jenkins workflow-basic-steps](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/):

| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `build` | Trigger another job | `build("downstream-job", parameters = mapOf("VERSION" to "1.0"))` |
| `catchError` | Catch errors and continue | `catchError("UNSTABLE") { sh("risky-command") }` |
| `deleteDir` | Delete workspace | `deleteDir()` |
| `dir` | Change directory | `dir("subdir") { sh("pwd") }` |
| `error` | Fail the build | `error("Build failed!")` |
| `fileExists` | Check file existence | `val exists = fileExists("pom.xml")` |
| `isUnix` | Check OS type | `val unix = isUnix()` |
| `mail` | Send email | `mail("dev@company.com", "Build Complete")` |
| `milestone` | Set milestone | `milestone(1, "Build Complete")` |
| `node` | Allocate node | `node("linux") { sh("uname -a") }` |
| `pwd` | Get current directory | `val dir = pwd()` |
| `readFile` | Read file content | `val content = readFile("README.md")` |
| `retry` | Retry on failure | `retry(3) { sh("flaky-command") }` |
| `script` | Execute script | `script("println 'Hello from Groovy!'")` |
| `sleep` | Sleep/wait | `sleep(5)` or `sleepMinutes(2)` |
| `stage` | Nested stage | `stage("substage") { echo("nested") }` |
| `timeout` | Set timeout | `timeout(10, MINUTES) { sh("long-command") }` |
| `tool` | Load tool | `val mvn = tool("maven-3.8")` |
| `unstable` | Mark unstable | `unstable("Tests failed")` |
| `waitUntil` | Wait for condition | `waitUntil("ready") { sh("check-status") }` |
| `warnError` | Warn on error | `warnError("Non-critical") { sh("optional-step") }` |
| `withEnv` | Set environment | `withEnv("PATH=/usr/bin") { sh("which java") }` |
| `writeFile` | Write file | `writeFile("output.txt", "Hello World")` |

### Pipeline Utility Steps

Equivalentes a [Jenkins pipeline-utility-steps](https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/):

#### File Operations
| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `findFiles` | Find files by pattern | `val files = findFiles("**/*.java")` |
| `readJSON` | Parse JSON | `val data = readJSON("config.json")` |
| `writeJSON` | Write JSON | `writeJSON("output.json", mapOf("key" to "value"))` |
| `readYaml` | Parse YAML | `val config = readYaml("deployment.yaml")` |
| `writeYaml` | Write YAML | `writeYaml("config.yaml", mapOf("version" to "1.0"))` |
| `readCSV` | Parse CSV | `val records = readCSV("data.csv")` |
| `writeCSV` | Write CSV | `writeCSV("output.csv", listOf(mapOf("id" to 1)))` |
| `readProperties` | Parse properties | `val props = readProperties("app.properties")` |
| `writeProperties` | Write properties | `writeProperties("build.properties", mapOf("version" to "1.0"))` |
| `readManifest` | Read JAR manifest | `val manifest = readManifest()` |

#### Archive Operations
| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `zip` | Create ZIP archive | `zip("archive.zip", "**/*.txt")` |
| `unzip` | Extract ZIP | `unzip("archive.zip", dir = "extracted")` |
| `tar` | Create TAR archive | `tar("backup.tar.gz", "**/*", compress = true)` |
| `untar` | Extract TAR | `untar("backup.tar.gz")` |

#### Encoding & Hashing
| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `base64Encode` | Encode to Base64 | `val encoded = base64Encode("file.txt")` |
| `base64Decode` | Decode from Base64 | `base64Decode(encoded, "decoded.txt")` |
| `sha1` | Calculate SHA1 | `val hash = sha1("file.txt")` |
| `sha256` | Calculate SHA256 | `val hash = sha256("file.txt")` |
| `md5` | Calculate MD5 | `val hash = md5("file.txt")` |

#### HTTP Operations
| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `httpRequest` | Make HTTP request | `val response = httpRequest("https://api.github.com/user")` |

#### Utilities
| Step | Descripción | Ejemplo |
|------|-------------|---------|
| `compareVersions` | Compare version strings | `val result = compareVersions("1.0.0", "1.1.0")` |
| `nodesByLabel` | Get nodes by label | `val nodes = nodesByLabel("linux")` |
| `libraryResource` | Load library resource | `val script = libraryResource("scripts/deploy.sh")` |
| `touch` | Touch file | `touch("timestamp.txt")` |

## 🎯 Ejemplos Avanzados

### CI/CD Pipeline Completo

```kotlin
pipeline("ci-cd-example") {
    parameters {
        string("BRANCH", "main", "Branch to build")
        choice("ENVIRONMENT", listOf("dev", "staging", "prod"), "Target environment")
        booleanParam("DEPLOY", true, "Deploy after build")
    }
    
    environment {
        "BUILD_VERSION" to "${env.BUILD_NUMBER}"
        "DOCKER_REGISTRY" to "registry.company.com"
    }
    
    stages {
        stage("Checkout") {
            steps {
                echo("🚀 Starting CI/CD for ${params.BRANCH}")
                
                checkout(scm = SCMConfig.Git(
                    url = "https://github.com/company/app.git",
                    branch = params.BRANCH
                ))
                
                // Load build configuration
                val buildConfig = readYaml("build.yaml")
                echo("Build config: $buildConfig")
            }
        }
        
        stage("Build") {
            steps {
                dir("app") {
                    withEnv("NODE_ENV=production") {
                        sh("npm ci")
                        sh("npm run build")
                        
                        // Generate build metadata
                        writeJSON("build-info.json", mapOf(
                            "version" to env.BUILD_VERSION,
                            "branch" to params.BRANCH,
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                }
                
                milestone(1, "Build Complete")
            }
        }
        
        stage("Test") {
            parallel(failFast = true) {
                "Unit Tests" {
                    dir("app") {
                        sh("npm run test:unit")
                        publishTestResults("test-results.xml")
                    }
                }
                
                "Integration Tests" {
                    timeout(15, MINUTES) {
                        sh("docker-compose up -d")
                        
                        waitUntil("services ready") {
                            sh("docker-compose ps | grep healthy")
                        }
                        
                        try {
                            sh("npm run test:integration")
                        } finally {
                            sh("docker-compose down")
                        }
                    }
                }
                
                "Security Scan" {
                    retry(3) {
                        sh("npm audit --audit-level=high")
                    }
                }
            }
        }
        
        stage("Package") {
            steps {
                // Create Docker image
                sh("docker build -t app:${env.BUILD_VERSION} .")
                sh("docker tag app:${env.BUILD_VERSION} ${env.DOCKER_REGISTRY}/app:${env.BUILD_VERSION}")
                
                // Archive artifacts
                zip("artifacts.zip", "app/dist/**/*")
                
                // Generate deployment manifest
                writeYaml("k8s-deployment.yaml", mapOf(
                    "apiVersion" to "apps/v1",
                    "kind" to "Deployment",
                    "metadata" to mapOf("name" to "app"),
                    "spec" to mapOf(
                        "replicas" to 3,
                        "template" to mapOf(
                            "spec" to mapOf(
                                "containers" to listOf(
                                    mapOf(
                                        "name" to "app",
                                        "image" to "${env.DOCKER_REGISTRY}/app:${env.BUILD_VERSION}"
                                    )
                                )
                            )
                        )
                    )
                ))
            }
        }
        
        stage("Deploy") {
            when {
                allOf {
                    expression { params.DEPLOY }
                    branch("main")
                }
            }
            
            steps {
                // Push Docker image
                sh("docker push ${env.DOCKER_REGISTRY}/app:${env.BUILD_VERSION}")
                
                // Deploy to Kubernetes
                withEnv("KUBECONFIG=/home/jenkins/.kube/config") {
                    sh("kubectl apply -f k8s-deployment.yaml")
                    
                    waitUntil("deployment ready") {
                        sh("kubectl rollout status deployment/app")
                    }
                }
                
                // Health check
                val healthResponse = httpRequest("https://app.${params.ENVIRONMENT}.com/health") {
                    get()
                    json()
                    timeout = 30
                }
                echo("Health check: $healthResponse")
                
                milestone(2, "Deploy Complete")
            }
        }
        
        stage("Notify") {
            steps {
                // Trigger downstream job
                build("integration-tests") {
                    string("APP_VERSION", env.BUILD_VERSION)
                    string("ENVIRONMENT", params.ENVIRONMENT)
                }
                
                // Send notification
                mail("team@company.com", "Deployment ${env.BUILD_VERSION} Complete") {
                    body = """
                        🚀 Deployment successful!
                        
                        Version: ${env.BUILD_VERSION}
                        Environment: ${params.ENVIRONMENT}
                        Build: ${env.BUILD_URL}
                    """.trimIndent()
                }
            }
        }
    }
    
    post {
        always {
            archiveArtifacts("**/*.{json,yaml,zip}")
            deleteDir()
        }
        
        failure {
            mail("team@company.com", "🔥 Pipeline Failed") {
                body = "Pipeline failed at ${env.STAGE_NAME}. Check ${env.BUILD_URL}"
                attachLog = true
            }
        }
    }
}
```

### Operaciones con Archivos

```kotlin
stage("File Processing") {
    steps {
        // JSON processing
        val config = readJSON("config.json")
        val updatedConfig = config + mapOf("timestamp" to System.currentTimeMillis())
        writeJSON("updated-config.json", updatedConfig, pretty = true)
        
        // YAML processing
        val deployment = readYaml("deployment.yaml")
        writeYaml("processed-deployment.yaml", deployment)
        
        // Properties with interpolation
        val props = readProperties("app.properties", interpolate = true)
        writeProperties("build.properties", props + mapOf("build.number" to env.BUILD_NUMBER))
        
        // CSV processing
        val users = readCSV("users.csv")
        val processedUsers = users.map { user ->
            user + mapOf("processed_at" to System.currentTimeMillis())
        }
        writeCSV("processed-users.csv", processedUsers)
        
        // Archive operations
        zip("data-backup.zip", "**/*.{json,yaml,properties,csv}")
        
        // Calculate checksums
        val backupHash = sha256("data-backup.zip")
        writeFile("backup.sha256", "$backupHash  data-backup.zip")
        
        echo("Backup hash: $backupHash")
    }
}
```

### HTTP API Integration

```kotlin
stage("API Integration") {
    steps {
        // GET request
        val userData = httpRequest("https://api.github.com/user") {
            get()
            json()
            auth("bearer ${env.GITHUB_TOKEN}")
        }
        
        // POST with JSON payload
        val createResponse = httpRequest("https://api.service.com/resources") {
            post()
            json()
            body("""
                {
                    "name": "MyResource",
                    "version": "${env.BUILD_VERSION}",
                    "metadata": ${writeJSON("", mapOf("build" to env.BUILD_NUMBER))}
                }
            """.trimIndent())
            auth("api-key ${env.API_KEY}")
            timeout = 60
        }
        
        // PUT request
        val updateResponse = httpRequest("https://api.service.com/resources/123") {
            put()
            json()
            body("""{"status": "deployed"}""")
        }
        
        echo("API responses: GET=$userData, POST=$createResponse, PUT=$updateResponse")
    }
}
```

## 🔧 Configuración

### Registrar la Extensión

```kotlin
val extensionRegistry = ExtensionRegistry(stepExecutorRegistry, libraryManager)

// Auto-load from standard locations
extensionRegistry.autoLoadExtensions()

// Or install specific version
extensionRegistry.installExtension(
    groupId = "dev.rubentxu.hodei",
    artifactId = "pipeline-steps-library",
    version = "1.0.0"
)
```

### Configurar en Pipeline

```kotlin
// Import DSL functions
import dev.rubentxu.hodei.pipelines.steps.dsl.*

// Use in pipeline
pipeline("my-pipeline") {
    stages {
        stage("example") {
            steps {
                // All steps are now available
                echo("Hello from Hodei!")
                val data = readJSON("config.json")
                writeYaml("output.yaml", data)
            }
        }
    }
}
```

## 🧪 Testing

La librería incluye tests exhaustivos para todos los steps:

```bash
# Run all tests
./gradlew test

# Run specific test category
./gradlew test --tests "*WorkflowBasicStepsTest"
./gradlew test --tests "*PipelineUtilityStepsTest"
```

## 📝 Documentación

- [Jenkins workflow-basic-steps](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/)
- [Jenkins pipeline-utility-steps](https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/)
- [Kotlin DSL Guide](https://kotlinlang.org/docs/type-safe-builders.html)

## 🤝 Contribuir

1. Fork el repositorio
2. Crea una feature branch (`git checkout -b feature/new-step`)
3. Commit tus cambios (`git commit -am 'Add new step'`)
4. Push a la branch (`git push origin feature/new-step`)
5. Crea un Pull Request

## 📄 Licencia

Este proyecto está licenciado bajo la MIT License - ver el archivo [LICENSE](LICENSE) para detalles.

## 🆕 Changelog

### v1.0.0
- ✨ Implementación inicial de workflow-basic-steps
- ✨ Implementación inicial de pipeline-utility-steps  
- ✨ DSL type-safe completo
- ✨ Sistema de extensiones
- ✨ Documentación y ejemplos completos
- ✨ Tests exhaustivos

---

**Desarrollado con ❤️ por el equipo de Hodei Pipelines**