#!/usr/bin/env pipeline-dsl

/**
 * Pipeline de ejemplo "Hello World" para Hodei Pipelines
 * 
 * Este pipeline demuestra:
 * - Configuración básica de pipeline
 * - Stages y steps
 * - Ejecución de comandos
 * - Manejo de output
 * - Artefactos simples
 */

pipeline {
    name = "Hello World Pipeline"
    description = "Pipeline de demostración básico que muestra las capacidades fundamentales"
    
    // Variables de entorno del pipeline
    environment {
        put("GREETING", "Hello from Hodei Pipelines!")
        put("BUILD_NUMBER", "1.0.0")
        put("ENVIRONMENT", "demo")
    }
    
    // Parámetros configurables
    parameters {
        string("TARGET_ENV", "development", "Environment target")
        boolean("VERBOSE", true, "Enable verbose logging")
        integer("TIMEOUT", 30, "Timeout in seconds")
    }
    
    // Stage de preparación
    stage("prepare") {
        description = "Preparación del entorno y validaciones"
        
        step("environment-check") {
            description = "Verificar variables de entorno"
            run {
                println("🔍 Checking environment...")
                println("Greeting: ${env["GREETING"]}")
                println("Build Number: ${env["BUILD_NUMBER"]}")
                println("Target Environment: ${params["TARGET_ENV"]}")
                println("Verbose Mode: ${params["VERBOSE"]}")
                
                // Simular validación
                if (params["VERBOSE"] as Boolean) {
                    println("✅ Verbose mode enabled")
                }
                
                "Environment check completed"
            }
        }
        
        step("system-info") {
            description = "Mostrar información del sistema"
            run {
                println("🖥️ System Information:")
                println("Java Version: ${System.getProperty("java.version")}")
                println("OS: ${System.getProperty("os.name")}")
                println("Architecture: ${System.getProperty("os.arch")}")
                println("User: ${System.getProperty("user.name")}")
                println("Working Directory: ${System.getProperty("user.dir")}")
                
                "System info collected"
            }
        }
    }
    
    // Stage principal de construcción
    stage("build") {
        description = "Construcción del proyecto de ejemplo"
        
        step("compile") {
            description = "Compilar código fuente simulado"
            run {
                println("🔨 Starting compilation...")
                
                // Simular compilación
                for (i in 1..5) {
                    println("Compiling module $i/5...")
                    Thread.sleep(500) // Simular tiempo de compilación
                }
                
                println("✅ Compilation successful!")
                "Compilation completed"
            }
        }
        
        step("package") {
            description = "Empaquetar artefactos"
            run {
                println("📦 Creating package...")
                
                // Simular empaquetado
                val packageName = "hello-world-${env["BUILD_NUMBER"]}.jar"
                println("Creating package: $packageName")
                
                // Crear archivo de ejemplo (simulado)
                val content = """
                    Package: $packageName
                    Version: ${env["BUILD_NUMBER"]}
                    Environment: ${params["TARGET_ENV"]}
                    Created: ${java.time.LocalDateTime.now()}
                """.trimIndent()
                
                println("Package contents:")
                println(content)
                
                // Simular guardado del artefacto
                artifacts.produce("package", packageName, content.toByteArray())
                
                println("✅ Package created successfully!")
                packageName
            }
        }
    }
    
    // Stage de testing
    stage("test") {
        description = "Ejecutar tests del proyecto"
        
        step("unit-tests") {
            description = "Ejecutar tests unitarios"
            run {
                println("🧪 Running unit tests...")
                
                val tests = listOf(
                    "HelloWorldTest",
                    "EnvironmentTest", 
                    "ConfigurationTest",
                    "IntegrationTest"
                )
                
                tests.forEach { test ->
                    println("Running $test...")
                    Thread.sleep(200)
                    println("✅ $test PASSED")
                }
                
                println("✅ All unit tests passed!")
                "Unit tests completed: ${tests.size} tests passed"
            }
        }
        
        step("integration-tests") {
            description = "Ejecutar tests de integración"
            run {
                println("🔗 Running integration tests...")
                
                // Simular tests de integración
                println("Testing API endpoints...")
                Thread.sleep(300)
                println("Testing database connections...")
                Thread.sleep(300)
                println("Testing external services...")
                Thread.sleep(300)
                
                println("✅ Integration tests passed!")
                "Integration tests completed"
            }
        }
    }
    
    // Stage de despliegue
    stage("deploy") {
        description = "Desplegar aplicación al entorno objetivo"
        condition = { params["TARGET_ENV"] != "local" }
        
        step("deploy-to-env") {
            description = "Desplegar al entorno ${params["TARGET_ENV"]}"
            run {
                val targetEnv = params["TARGET_ENV"]
                println("🚀 Deploying to $targetEnv environment...")
                
                // Simular despliegue
                println("Preparing deployment package...")
                Thread.sleep(500)
                println("Uploading to $targetEnv servers...")
                Thread.sleep(800)
                println("Configuring application...")
                Thread.sleep(400)
                println("Starting services...")
                Thread.sleep(600)
                
                println("✅ Deployment to $targetEnv completed!")
                "Deployed to $targetEnv"
            }
        }
        
        step("smoke-tests") {
            description = "Ejecutar smoke tests post-despliegue"
            run {
                println("🔥 Running smoke tests...")
                
                // Simular smoke tests
                println("Testing application startup...")
                Thread.sleep(300)
                println("Testing basic functionality...")
                Thread.sleep(400)
                println("Testing health endpoints...")
                Thread.sleep(200)
                
                println("✅ Smoke tests passed!")
                "Smoke tests completed"
            }
        }
    }
    
    // Stage de finalización
    stage("finalize") {
        description = "Tareas de finalización y notificación"
        
        step("generate-report") {
            description = "Generar reporte de ejecución"
            run {
                println("📊 Generating execution report...")
                
                val report = """
                    ═══════════════════════════════════════
                    🎉 HODEI PIPELINES EXECUTION REPORT
                    ═══════════════════════════════════════
                    
                    Pipeline: ${pipeline.name}
                    Build: ${env["BUILD_NUMBER"]}
                    Environment: ${params["TARGET_ENV"]}
                    
                    ✅ Stages Completed:
                    - prepare: Environment setup ✅
                    - build: Code compilation and packaging ✅  
                    - test: Unit and integration testing ✅
                    - deploy: Environment deployment ✅
                    - finalize: Report generation ✅
                    
                    📦 Artifacts Generated:
                    - hello-world-${env["BUILD_NUMBER"]}.jar
                    
                    🕐 Execution Time: ~15 seconds
                    
                    ═══════════════════════════════════════
                    🚀 DEPLOYMENT SUCCESSFUL! 
                    ═══════════════════════════════════════
                """.trimIndent()
                
                println(report)
                
                // Guardar reporte como artefacto
                artifacts.produce("reports", "execution-report.txt", report.toByteArray())
                
                "Report generated successfully"
            }
        }
        
        step("notify") {
            description = "Enviar notificaciones"
            run {
                println("📧 Sending notifications...")
                
                // Simular notificaciones
                println("Sending email to development team...")
                println("Posting to Slack channel #deployments...")
                println("Updating deployment dashboard...")
                
                println("✅ Notifications sent!")
                "Notifications completed"
            }
        }
    }
}

// Configuración adicional del pipeline
onSuccess {
    println("🎉 Pipeline completed successfully!")
    println("Build ${env["BUILD_NUMBER"]} deployed to ${params["TARGET_ENV"]}")
}

onFailure { error ->
    println("❌ Pipeline failed: $error")
    println("Check logs for details and retry after fixing issues")
}

onFinally {
    println("🏁 Pipeline execution finished")
    println("Thank you for using Hodei Pipelines!")
}