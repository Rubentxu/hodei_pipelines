package dev.rubentxu.hodei.pipelines.steps.e2e

import dev.rubentxu.hodei.pipelines.dsl.builders.*
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineExecutor
import dev.rubentxu.hodei.pipelines.dsl.model.*
import dev.rubentxu.hodei.pipelines.steps.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Tests E2E de integración completa del Pipeline DSL con la librería de steps.
 * Verifica pipelines completos funcionando de extremo a extremo.
 */
class PipelineDSLIntegrationE2ETest : E2ETestBase() {
    
    @Test
    fun `complete CI pipeline should execute successfully`() = runBlocking {
        // Given - Un pipeline CI completo
        val pipeline = pipeline("ci-integration-test") {
            description("Complete CI pipeline integration test")
            
            agent {
                label("test-agent")
            }
            
            environment {
                "BUILD_VERSION" to "1.0.0"
                "NODE_ENV" to "test"
            }
            
            stages {
                stage("Setup") {
                    steps {
                        echo("🚀 Starting CI pipeline...")
                        
                        // Crear configuración del proyecto
                        writeJSON("package.json", mapOf(
                            "name" to "test-app",
                            "version" to "1.0.0",
                            "scripts" to mapOf(
                                "build" to "echo 'Building...'",
                                "test" to "echo 'Testing...'"
                            )
                        ), pretty = true)
                        
                        // Verificar entorno
                        val isUnixSystem = isUnix()
                        val currentDir = pwd()
                        
                        echo("Unix system: \$isUnixSystem")
                        echo("Working directory: \$currentDir")
                    }
                }
                
                stage("Build") {
                    steps {
                        echo("📦 Building application...")
                        
                        // Simular proceso de build
                        writeFile("dist/app.js", """
                            console.log('Hello from built app!');
                            module.exports = { version: '1.0.0' };
                        """.trimIndent())
                        
                        writeFile("dist/index.html", """
                            <!DOCTYPE html>
                            <html>
                            <head><title>Test App</title></head>
                            <body><h1>Test Application v1.0.0</h1></body>
                            </html>
                        """.trimIndent())
                        
                        // Calcular hash de los artefactos
                        val appHash = sha256("dist/app.js")
                        val htmlHash = sha256("dist/index.html")
                        
                        echo("App hash: \$appHash")
                        echo("HTML hash: \$htmlHash")
                        
                        // Generar metadata de build
                        writeJSON("build-metadata.json", mapOf(
                            "buildNumber" to 1,
                            "version" to "1.0.0",
                            "timestamp" to System.currentTimeMillis(),
                            "artifacts" to mapOf(
                                "app.js" to "\$appHash",
                                "index.html" to "\$htmlHash"
                            )
                        ))
                    }
                }
                
                stage("Test") {
                    parallel(failFast = true) {
                        "Unit Tests" {
                            echo("🧪 Running unit tests...")
                            sleep(1)
                            
                            // Generar resultados de tests
                            writeFile("test-results.xml", """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <testsuite name="UnitTests" tests="5" failures="0" errors="0">
                                    <testcase name="testBasicFunctionality" time="0.1"/>
                                    <testcase name="testConfiguration" time="0.2"/>
                                    <testcase name="testUtilities" time="0.15"/>
                                    <testcase name="testValidation" time="0.08"/>
                                    <testcase name="testIntegration" time="0.3"/>
                                </testsuite>
                            """.trimIndent())
                            
                            echo("✅ Unit tests passed")
                        }
                        
                        "Integration Tests" {
                            echo("🔗 Running integration tests...")
                            sleep(2)
                            
                            // Verificar artefactos existen
                            val appExists = fileExists("dist/app.js")
                            val htmlExists = fileExists("dist/index.html")
                            
                            echo("App artifact exists: \$appExists")
                            echo("HTML artifact exists: \$htmlExists")
                            
                            echo("✅ Integration tests passed")
                        }
                        
                        "Quality Checks" {
                            echo("📊 Running quality analysis...")
                            sleep(1)
                            
                            // Simular análisis de calidad
                            writeJSON("quality-report.json", mapOf(
                                "coverage" to 85.5,
                                "complexity" to "LOW",
                                "duplications" to 2.1,
                                "maintainability" to "A",
                                "security" to mapOf(
                                    "vulnerabilities" to 0,
                                    "hotspots" to 1
                                )
                            ))
                            
                            echo("✅ Quality checks passed")
                        }
                    }
                }
                
                stage("Package") {
                    steps {
                        echo("📦 Packaging artifacts...")
                        
                        // Crear archivo de distribución
                        zip("dist.zip", "dist/**/*")
                        
                        // Calcular hash del paquete
                        val packageHash = sha256("dist.zip")
                        echo("Package hash: \$packageHash")
                        
                        // Crear manifiesto de release
                        writeYaml("release-manifest.yaml", mapOf(
                            "version" to "1.0.0",
                            "build" to 1,
                            "artifacts" to listOf(
                                mapOf(
                                    "name" to "dist.zip",
                                    "hash" to "\$packageHash",
                                    "size" to 1024
                                )
                            ),
                            "metadata" to mapOf(
                                "buildTime" to System.currentTimeMillis(),
                                "environment" to "test"
                            )
                        ))
                        
                        echo("📋 Release manifest created")
                    }
                }
                
                stage("Archive") {
                    steps {
                        echo("📚 Archiving build artifacts...")
                        
                        // Encontrar todos los archivos generados
                        val allFiles = findFiles("**/*.{json,yaml,xml,zip,js,html}")
                        echo("Found \${allFiles.size} files to archive")
                        
                        // Crear backup completo
                        tar("build-backup.tar.gz", "**/*", compress = true)
                        
                        // Generar checksums
                        val backupHash = sha256("build-backup.tar.gz")
                        writeFile("checksums.txt", "sha256: \$backupHash build-backup.tar.gz")
                        
                        echo("✅ Build artifacts archived")
                    }
                }
            }
            
            post {
                always {
                    echo("🧹 Cleaning up...")
                    
                    // Generar reporte final
                    writeJSON("final-report.json", mapOf(
                        "status" to "SUCCESS",
                        "duration" to System.currentTimeMillis(),
                        "stages" to listOf("Setup", "Build", "Test", "Package", "Archive"),
                        "artifacts" to listOf("dist.zip", "build-backup.tar.gz"),
                        "metadata" to mapOf(
                            "version" to "1.0.0",
                            "environment" to "test"
                        )
                    ))
                }
                
                success {
                    echo("🎉 Pipeline completed successfully!")
                }
                
                failure {
                    echo("💥 Pipeline failed!")
                }
            }
        }
        
        // When - Ejecutar el pipeline completo
        val executor = PipelineExecutor(
            stepExecutorRegistry = stepExecutorRegistry,
            extensionRegistry = extensionRegistry
        )
        
        executor.execute(pipeline, pipelineContext)
        waitForCompletion(2000) // Esperar más tiempo para pipeline completo
        
        // Then - Verificar que todo se ejecutó correctamente
        assertOutputContains("🚀 Starting CI pipeline...")
        assertOutputContains("📦 Building application...")
        assertOutputContains("🧪 Running unit tests...")
        assertOutputContains("🔗 Running integration tests...")
        assertOutputContains("📊 Running quality analysis...")
        assertOutputContains("📦 Packaging artifacts...")
        assertOutputContains("📚 Archiving build artifacts...")
        assertOutputContains("🎉 Pipeline completed successfully!")
        
        // Verificar artefactos creados
        assertFileExists("package.json")
        assertFileExists("dist/app.js")
        assertFileExists("dist/index.html")
        assertFileExists("build-metadata.json")
        assertFileExists("test-results.xml")
        assertFileExists("quality-report.json")
        assertFileExists("dist.zip")
        assertFileExists("release-manifest.yaml")
        assertFileExists("build-backup.tar.gz")
        assertFileExists("checksums.txt")
        assertFileExists("final-report.json")
        
        // Verificar contenido de archivos clave
        assertFileContent("package.json") { it.contains("\"name\": \"test-app\"") }
        assertFileContent("build-metadata.json") { it.contains("\"version\": \"1.0.0\"") }
        assertFileContent("release-manifest.yaml") { it.contains("version: 1.0.0") }
        assertFileContent("final-report.json") { it.contains("\"status\": \"SUCCESS\"") }
        
        assertNoErrors()
    }
    
    @Test
    fun `deployment pipeline with conditional stages should work`() = runBlocking {
        // Given - Pipeline con stages condicionales
        val pipeline = pipeline("deployment-test") {
            description("Deployment pipeline with conditional execution")
            
            parameters {
                string("ENVIRONMENT", "staging", "Target environment")
                booleanParam("DEPLOY", true, "Deploy to environment")
                choice("STRATEGY", listOf("rolling", "blue-green", "canary"), "Deployment strategy")
            }
            
            environment {
                "TARGET_ENV" to "${params.ENVIRONMENT}"
                "DEPLOY_STRATEGY" to "${params.STRATEGY}"
            }
            
            stages {
                stage("Prepare") {
                    steps {
                        echo("🔧 Preparing deployment to \${params.ENVIRONMENT}")
                        
                        // Crear configuración específica del entorno
                        val envConfig = when ("${params.ENVIRONMENT}") {
                            "staging" -> mapOf(
                                "replicas" to 2,
                                "resources" to mapOf("cpu" to "500m", "memory" to "512Mi")
                            )
                            "production" -> mapOf(
                                "replicas" to 5,
                                "resources" to mapOf("cpu" to "1000m", "memory" to "1Gi")
                            )
                            else -> mapOf(
                                "replicas" to 1,
                                "resources" to mapOf("cpu" to "200m", "memory" to "256Mi")
                            )
                        }
                        
                        writeYaml("deployment-config.yaml", mapOf(
                            "environment" to "${params.ENVIRONMENT}",
                            "strategy" to "${params.STRATEGY}",
                            "config" to envConfig
                        ))
                        
                        echo("📝 Deployment configuration created")
                    }
                }
                
                stage("Build Image") {
                    when {
                        expression { "${params.DEPLOY}" == "true" }
                    }
                    
                    steps {
                        echo("🐳 Building Docker image...")
                        
                        // Simular build de imagen Docker
                        writeFile("Dockerfile", """
                            FROM node:18-alpine
                            WORKDIR /app
                            COPY package*.json ./
                            RUN npm ci --only=production
                            COPY . .
                            EXPOSE 3000
                            CMD ["npm", "start"]
                        """.trimIndent())
                        
                        // Simular proceso de build
                        sleep(2)
                        
                        val imageTag = "myapp:1.0.0-${System.currentTimeMillis()}"
                        echo("📷 Image built: \$imageTag")
                        
                        // Guardar tag de imagen
                        writeFile("image-tag.txt", imageTag)
                    }
                }
                
                stage("Deploy to Staging") {
                    when {
                        allOf {
                            expression { "${params.DEPLOY}" == "true" }
                            expression { "${params.ENVIRONMENT}" == "staging" }
                        }
                    }
                    
                    steps {
                        echo("🚀 Deploying to staging environment...")
                        
                        timeout(time = 10, unit = TimeUnit.MINUTES) {
                            // Simular despliegue
                            val deploymentSpec = readYaml("deployment-config.yaml")
                            echo("Using deployment config: \$deploymentSpec")
                            
                            val imageTag = readFile("image-tag.txt")
                            echo("Deploying image: \$imageTag")
                            
                            // Simular despliegue K8s
                            writeYaml("k8s-manifest.yaml", mapOf(
                                "apiVersion" to "apps/v1",
                                "kind" to "Deployment",
                                "metadata" to mapOf("name" to "myapp", "namespace" to "staging"),
                                "spec" to mapOf(
                                    "replicas" to 2,
                                    "template" to mapOf(
                                        "spec" to mapOf(
                                            "containers" to listOf(
                                                mapOf("name" to "app", "image" to imageTag.trim())
                                            )
                                        )
                                    )
                                )
                            ))
                            
                            sleep(3) // Simular tiempo de despliegue
                            
                            echo("✅ Deployed to staging successfully")
                        }
                    }
                }
                
                stage("Integration Tests") {
                    when {
                        allOf {
                            expression { "${params.DEPLOY}" == "true" }
                            anyOf {
                                expression { "${params.ENVIRONMENT}" == "staging" }
                                expression { "${params.ENVIRONMENT}" == "production" }
                            }
                        }
                    }
                    
                    steps {
                        echo("🧪 Running integration tests against \${params.ENVIRONMENT}...")
                        
                        retry(count = 3) {
                            // Simular health check
                            sleep(1)
                            echo("✅ Health check passed")
                        }
                        
                        // Simular pruebas de integración
                        parallel(failFast = false) {
                            "API Tests" {
                                echo("📡 Testing API endpoints...")
                                sleep(2)
                                writeJSON("api-test-results.json", mapOf(
                                    "total" to 15,
                                    "passed" to 15,
                                    "failed" to 0,
                                    "duration" to 2.5
                                ))
                            }
                            
                            "UI Tests" {
                                echo("🖥️ Testing UI components...")
                                sleep(3)
                                writeJSON("ui-test-results.json", mapOf(
                                    "total" to 8,
                                    "passed" to 8,
                                    "failed" to 0,
                                    "duration" to 3.2
                                ))
                            }
                            
                            "Performance Tests" {
                                echo("⚡ Running performance tests...")
                                sleep(2)
                                writeJSON("perf-test-results.json", mapOf(
                                    "avgResponseTime" to 150,
                                    "maxResponseTime" to 300,
                                    "throughput" to 1000,
                                    "errors" to 0
                                ))
                            }
                        }
                        
                        echo("✅ All integration tests passed")
                    }
                }
                
                stage("Deploy to Production") {
                    when {
                        allOf {
                            expression { "${params.DEPLOY}" == "true" }
                            expression { "${params.ENVIRONMENT}" == "production" }
                            expression { "${params.STRATEGY}" != "canary" }
                        }
                    }
                    
                    steps {
                        echo("🎯 Deploying to production environment...")
                        
                        // Milestone antes de producción
                        milestone(ordinal = 1, label = "Ready for Production")
                        
                        timeout(time = 20, unit = TimeUnit.MINUTES) {
                            val strategy = "${params.STRATEGY}"
                            echo("Using deployment strategy: \$strategy")
                            
                            when (strategy) {
                                "rolling" -> {
                                    echo("🔄 Performing rolling deployment...")
                                    sleep(5)
                                }
                                "blue-green" -> {
                                    echo("🔵🟢 Performing blue-green deployment...")
                                    sleep(4)
                                }
                                else -> {
                                    echo("📦 Performing standard deployment...")
                                    sleep(3)
                                }
                            }
                            
                            echo("✅ Production deployment completed")
                        }
                        
                        milestone(ordinal = 2, label = "Production Deployed")
                    }
                }
                
                stage("Canary Deployment") {
                    when {
                        allOf {
                            expression { "${params.DEPLOY}" == "true" }
                            expression { "${params.ENVIRONMENT}" == "production" }
                            expression { "${params.STRATEGY}" == "canary" }
                        }
                    }
                    
                    steps {
                        echo("🐦 Starting canary deployment...")
                        
                        // Desplegar 10% de tráfico
                        echo("Deploying canary with 10% traffic...")
                        sleep(2)
                        
                        // Monitorear métricas
                        waitUntil("canary metrics stable") {
                            echo("Checking canary metrics...")
                            sleep(1)
                            // Simular métricas estables
                        }
                        
                        // Incrementar a 50%
                        echo("Increasing canary traffic to 50%...")
                        sleep(2)
                        
                        // Completar despliegue
                        echo("Completing canary deployment...")
                        sleep(1)
                        
                        echo("✅ Canary deployment successful")
                    }
                }
            }
            
            post {
                always {
                    echo("📊 Generating deployment report...")
                    
                    val deploymentInfo = mapOf(
                        "environment" to "${params.ENVIRONMENT}",
                        "strategy" to "${params.STRATEGY}",
                        "deployed" to "${params.DEPLOY}",
                        "timestamp" to System.currentTimeMillis(),
                        "artifacts" to listOf("k8s-manifest.yaml", "deployment-config.yaml")
                    )
                    
                    writeJSON("deployment-report.json", deploymentInfo)
                }
                
                success {
                    echo("🎉 Deployment pipeline completed successfully!")
                    
                    // Notificación de éxito (simulada)
                    echo("📧 Sending success notification...")
                }
                
                failure {
                    echo("💥 Deployment pipeline failed!")
                    
                    // Notificación de fallo (simulada)
                    echo("📧 Sending failure notification...")
                }
            }
        }
        
        // When - Ejecutar con diferentes parámetros
        pipelineContext.setVariable("params.ENVIRONMENT", "staging")
        pipelineContext.setVariable("params.DEPLOY", true)
        pipelineContext.setVariable("params.STRATEGY", "rolling")
        
        val executor = PipelineExecutor(
            stepExecutorRegistry = stepExecutorRegistry,
            extensionRegistry = extensionRegistry
        )
        
        executor.execute(pipeline, pipelineContext)
        waitForCompletion(3000)
        
        // Then - Verificar ejecución condicional correcta
        assertOutputContains("🔧 Preparing deployment to staging")
        assertOutputContains("🐳 Building Docker image...")
        assertOutputContains("🚀 Deploying to staging environment...")
        assertOutputContains("🧪 Running integration tests against staging...")
        assertOutputContains("🎉 Deployment pipeline completed successfully!")
        
        // Verificar que producción NO se ejecutó
        val output = getOutputText()
        assertTrue(!output.contains("🎯 Deploying to production environment..."))
        assertTrue(!output.contains("🐦 Starting canary deployment..."))
        
        // Verificar artefactos
        assertFileExists("deployment-config.yaml")
        assertFileExists("Dockerfile")
        assertFileExists("image-tag.txt")
        assertFileExists("k8s-manifest.yaml")
        assertFileExists("api-test-results.json")
        assertFileExists("ui-test-results.json")
        assertFileExists("perf-test-results.json")
        assertFileExists("deployment-report.json")
        
        assertNoErrors()
    }
    
    @Test
    fun `error handling and recovery pipeline should work`() = runBlocking {
        // Given - Pipeline con manejo de errores
        val pipeline = pipeline("error-handling-test") {
            description("Pipeline testing error handling and recovery")
            
            stages {
                stage("Setup") {
                    steps {
                        echo("🔧 Setting up error handling test...")
                        
                        writeJSON("config.json", mapOf(
                            "errorMode" to true,
                            "retryAttempts" to 3,
                            "fallbackEnabled" to true
                        ))
                    }
                }
                
                stage("Risky Operations") {
                    steps {
                        echo("⚠️ Performing risky operations...")
                        
                        // Operación que puede fallar pero continuar
                        warnError("Non-critical operation failed") {
                            echo("This might fail but pipeline should continue...")
                            // Simular fallo no crítico
                        }
                        
                        // Retry con eventual éxito
                        retry(count = 3) {
                            echo("Attempting flaky operation...")
                            // Esta operación eventualmente tendrá éxito
                        }
                        
                        // Catch error y manejar
                        catchError(buildResult = "UNSTABLE", message = "Handled error gracefully") {
                            echo("Attempting operation that might fail...")
                            // Simular operación con posible error
                        }
                        
                        echo("✅ Risky operations completed")
                    }
                }
                
                stage("Validation") {
                    steps {
                        echo("✅ Validating results...")
                        
                        // Verificar que los archivos necesarios existen
                        val configExists = fileExists("config.json")
                        echo("Config file exists: \$configExists")
                        
                        // Leer y validar configuración
                        val config = readJSON("config.json")
                        echo("Configuration loaded successfully")
                        
                        // Crear reporte de validación
                        writeJSON("validation-report.json", mapOf(
                            "configExists" to true,
                            "configValid" to true,
                            "riskyOperationsCompleted" to true,
                            "buildResult" to "UNSTABLE" // Debido a catchError
                        ))
                        
                        echo("📋 Validation report created")
                    }
                }
            }
            
            post {
                always {
                    echo("🧹 Cleaning up after error handling test...")
                    
                    val buildResult = getVariable<String>("currentBuild.result") ?: "SUCCESS"
                    
                    writeJSON("final-status.json", mapOf(
                        "finalResult" to buildResult,
                        "timestamp" to System.currentTimeMillis(),
                        "message" to "Error handling pipeline completed"
                    ))
                }
                
                unstable {
                    echo("⚠️ Build is unstable but handled gracefully")
                }
            }
        }
        
        // When
        val executor = PipelineExecutor(
            stepExecutorRegistry = stepExecutorRegistry,
            extensionRegistry = extensionRegistry
        )
        
        executor.execute(pipeline, pipelineContext)
        waitForCompletion(1500)
        
        // Then
        assertOutputContains("🔧 Setting up error handling test...")
        assertOutputContains("⚠️ Performing risky operations...")
        assertOutputContains("✅ Validating results...")
        assertOutputContains("📋 Validation report created")
        assertOutputContains("🧹 Cleaning up after error handling test...")
        
        // Verificar archivos creados
        assertFileExists("config.json")
        assertFileExists("validation-report.json")
        assertFileExists("final-status.json")
        
        // Verificar contenido
        assertFileContent("config.json") { it.contains("\"errorMode\": true") }
        assertFileContent("validation-report.json") { it.contains("\"configValid\": true") }
        assertFileContent("final-status.json") { it.contains("\"message\": \"Error handling pipeline completed\"") }
        
        assertNoErrors()
    }
}