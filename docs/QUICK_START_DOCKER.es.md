# Guía de Inicio Rápido: Hodei Pipelines con Docker

Esta guía completa te muestra cómo iniciar el orquestador y usar el CLI empresarial de Hodei (`hp`) para gestionar la ejecución distribuida de trabajos con Docker en tu máquina local.

## Prerrequisitos

### Para Instalación Estándar
- **Java 17+** instalado
- **Docker** instalado y funcionando
- **Gradle** instalado (no usar gradlew según configuración del proyecto)

### Para Binario Nativo (Opcional pero Recomendado)
- **GraalVM 21+** instalado (para compilación nativa)
- Alternativa: Descargar binarios nativos pre-compilados desde releases

## Paso 1: Compilar el Proyecto

```bash
# Desde el directorio raíz del proyecto
gradle clean build -x test
```

## Paso 2: Iniciar el Orquestador

```bash
# Opción 1: Usando gradle
gradle :orchestrator:run

# Opción 2: Usando el JAR generado
java -jar orchestrator/build/libs/orchestrator-all.jar

# El orquestador se iniciará en http://localhost:8080
```

El orquestador automáticamente:
- **Inicializa usuarios por defecto** (admin/admin123, user/user123, moderator/mod123)
- **Descubre el entorno Docker** y lo registra como un pool de recursos
- **Crea plantillas de workers por defecto** para diferentes escenarios
- Expone la API REST en el puerto 8080
- Expone el servidor gRPC en el puerto 9090 (para comunicación con workers)

Salida esperada al inicio:
```
🚀 Starting system bootstrap...
🔐 Initializing default users...
✅ Created user: admin with roles: [ADMIN]
✅ Created user: user with roles: [USER]  
✅ Created user: moderator with roles: [MODERATOR]

🔑 Credenciales de Usuario por Defecto (para testing del CLI):
==================================================
👑 Admin:     admin / admin123
👤 User:      user / user123
🛡️ Moderator: moderator / mod123
==================================================
💡 Usa estas credenciales con: hp login http://localhost:8080

🏗️ Entorno Docker registrado como pool de recursos
📋 Plantillas de worker por defecto creadas: 7 templates
==================================================
🔗 Uso del CLI: hp login http://localhost:8080
🔗 Health Check: curl http://localhost:8080/v1/health
```

## Paso 3: Configurar el CLI de Hodei (`hp`)

### Opción A: Binario Nativo (Recomendado) ⚡

Para startup más rápido y sin dependencia de JVM:

```bash
# Compilar binario nativo (requiere GraalVM)
gradle :hodei-pipelines-cli:nativeCompile

# Usar el binario nativo directamente
./hodei-pipelines-cli/build/native/nativeCompile/hp version

# O crear distribución completa
gradle :hodei-pipelines-cli:createNativeDistributions

# Instalar a nivel del sistema (opcional)
sudo cp hodei-pipelines-cli/build/distributions/native/linux-x64/hp /usr/local/bin/
hp version
```

**Beneficios del binario nativo:**
- ⚡ **Inicio ultra-rápido** (sin overhead de JVM)
- 🚀 **No requiere Java** (ejecutable independiente)
- 📦 **Distribución de archivo único** (58MB autocontenido)
- 🔧 **Todas las funciones CLI** (35+ comandos incluidos)

### Opción B: Distribución JAR (Tradicional)

Si GraalVM no está disponible:

```bash
# Compilar el CLI
gradle :hodei-pipelines-cli:assemble

# Extraer la distribución
cd hodei-pipelines-cli/build/distributions
tar -xf hodei-pipelines-cli.tar
cd hodei-pipelines-cli/bin

# Hacer ejecutable y añadir al PATH (opcional)
chmod +x hp
export PATH=$PATH:$(pwd)

# Verificar la instalación
./hp version
```

Salida esperada:
```
🚀 Hodei Pipelines CLI
Version: 1.1.0-SNAPSHOT
Build: $(git rev-parse --short HEAD)

🖥️ Server: Not connected
💡 Use 'hp login <url>' to connect to an orchestrator
```

### Instalación de GraalVM (Para Binario Nativo)

Si quieres compilar binarios nativos tú mismo:

```bash
# Instalar GraalVM usando SDKMAN (recomendado)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce

# Verificar instalación de GraalVM
java -version
native-image --version
```

Salida esperada de GraalVM:
```
openjdk version "21.0.2" 2024-01-16
OpenJDK Runtime Environment GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)
```

## Paso 4: Autenticarse en el Orquestador

```bash
# Login con credenciales de admin
hp login http://localhost:8080 --username admin --password admin123

# O login interactivo (solicita credenciales)
hp login http://localhost:8080

# Verificar login
hp whoami
```

Salida esperada:
```
✅ Login successful!

Username: admin
Orchestrator URL: http://localhost:8080
Context: default
```

## Paso 5: Verificar el Estado del Sistema

```bash
# Verificar salud del orquestador
hp health

# Verificar estado completo del sistema
hp status

# Listar recursos disponibles
hp pool list
hp template list
hp worker list
```

Salida esperada:
```bash
$ hp health
🏥 Health Check: http://localhost:8080
Overall: healthy
Timestamp: 2024-07-04T10:30:15Z

Components:
  ✅ database: Healthy
  ✅ docker: Connected
  ✅ grpc-server: Running on port 9090

$ hp status
📊 System Status
════════════════
🔗 Connected to: http://localhost:8080

✅ Overall Health: healthy

🏊 Resource Pools: 1
   Active: 1
   Total Capacity: 5 workers

📋 Jobs: 0 total
   🏃 Running: 0
   ⏳ Queued: 0
   ✅ Completed: 0
   ❌ Failed: 0

👷 Workers: 0 total
   🟢 Idle: 0
   🟡 Busy: 0
   🔴 Offline: 0

$ hp pool list
🏊 Resource Pools:

• auto-discovered-docker (pool-123)
  Type: docker | Status: active
  Provider: docker
  Created: 2024-07-04T10:30:15Z
```

## Paso 6: Enviar tu Primer Trabajo

### Envío Básico de Trabajo

```bash
# Enviar un trabajo usando un archivo de pipeline
hp job submit examples/hello-world.pipeline.kts --name "mi-primer-trabajo"

# Enviar con opciones adicionales
hp job submit examples/hello-world.pipeline.kts \
  --name "mi-primer-trabajo" \
  --priority high \
  --pool pool-123 \
  --timeout 300
```

Salida esperada:
```
📤 Submitting job from 'examples/hello-world.pipeline.kts'...

✅ Job submitted successfully!
   Job ID: job-abc123-def456
   Status: queued
   Queue Position: 1

💡 Track job progress with: hp job status job-abc123-def456
💡 View logs with: hp job logs job-abc123-def456
```

### Validar Antes de Enviar (Dry-run)

```bash
# Probar tu pipeline sin enviarlo realmente
hp job submit examples/hello-world.pipeline.kts --dry-run
```

Salida esperada:
```
🔍 Dry-run mode: Validating pipeline...
  Name: hello-world
  Priority: normal
✅ Pipeline validation successful. Job would be submitted.
```

## Paso 7: Monitorear la Ejecución del Trabajo

### Verificar Estado del Trabajo

```bash
# Obtener información detallada del trabajo
hp job status job-abc123-def456

# Obtener detalles completos del trabajo (estilo kubectl)
hp job describe job-abc123-def456
```

Salida esperada:
```bash
$ hp job status job-abc123-def456
📊 Job Status
═════════════
ID:          job-abc123-def456
Name:        mi-primer-trabajo
Type:        pipeline
Status:      running
Priority:    normal

⏱️ Timeline:
  Created:   2024-07-04T10:35:00Z
  Started:   2024-07-04T10:35:05Z

🔧 Execution:
  Pool:   pool-123
  Worker: worker-789

💡 View logs with: hp job logs job-abc123-def456 --follow
```

### Monitoreo de Logs en Tiempo Real

```bash
# Seguir logs en tiempo real con salida codificada por colores
hp job logs job-abc123-def456 --follow

# Mostrar las últimas 50 líneas
hp job logs job-abc123-def456 --tail 50

# Mostrar logs desde un momento específico
hp job logs job-abc123-def456 --since 2024-07-04T10:30:00Z
```

Salida esperada de logs:
```
📄 Following job logs for: job-abc123-def456
Press Ctrl+C to stop...
─────────────────────────────────────────
[10:35:05] ℹ️ Starting pipeline execution...
[10:35:06] ℹ️ Loading dependencies...
[10:35:08] ℹ️ Executing stage: compile
[10:35:10] ℹ️ Hello from Hodei Pipelines!
[10:35:11] ℹ️ Build Number: 1.0.0
[10:35:12] ✅ Pipeline completed successfully!
```

### Listar y Filtrar Trabajos

```bash
# Listar todos los trabajos
hp job list

# Listar solo trabajos en ejecución
hp job list --status running

# Listar solo trabajos fallidos
hp job list --status failed
```

## Paso 8: Gestión Avanzada de Recursos

### Gestión de Pools

```bash
# Crear un nuevo pool de recursos
hp pool create --name mi-docker-pool --type docker --max-workers 10

# Crear con validación dry-run
hp pool create --name test-pool --type kubernetes --dry-run

# Obtener información detallada del pool
hp pool describe pool-123

# Eliminar un pool (con confirmación)
hp pool delete pool-123

# Forzar eliminación sin confirmación
hp pool delete pool-123 --force
```

### Gestión de Workers

```bash
# Listar workers con filtrado
hp worker list --pool pool-123 --status idle

# Obtener información detallada del worker
hp worker describe worker-789

# Verificar estado del worker
hp worker status worker-789
```

### Gestión de Plantillas

```bash
# Crear una plantilla personalizada
hp template create \
  --name nodejs-template \
  --description "Entorno de desarrollo Node.js 18" \
  --type docker \
  --file nodejs-template.json

# Validar plantilla antes de crear
hp template create \
  --name test-template \
  --description "Plantilla de prueba" \
  --file template.json \
  --dry-run

# Mostrar detalles de plantilla
hp template describe template-123

# Listar plantillas por tipo
hp template list --type docker
```

## Paso 9: Acceso Interactivo (Comandos Shell)

### Acceso Shell a Workers

```bash
# Ejecutar comandos en un worker
hp worker exec worker-789 -- ls -la

# Iniciar un shell interactivo en un worker
hp worker shell worker-789

# Usar un shell específico
hp worker shell worker-789 --shell /bin/zsh
```

### Acceso al Contexto del Trabajo

```bash
# Ejecutar comandos en el contexto de un trabajo en ejecución
hp job exec job-abc123-def456 -- cat /logs/output.log

# Iniciar un shell interactivo en el entorno del trabajo
hp job shell job-abc123-def456

# Verificar procesos del trabajo
hp job exec job-abc123-def456 -- ps aux
```

> **Nota**: Los comandos de acceso shell están actualmente en implementación de Fase 1 y requieren soporte de streaming gRPC en el lado del servidor.

## Paso 10: Gestión del Ciclo de Vida de Trabajos

### Cancelar Trabajos en Ejecución

```bash
# Cancelar con prompt de confirmación
hp job cancel job-abc123-def456

# Cancelar con una razón
hp job cancel job-abc123-def456 --reason "Tiempo de construcción excedido"

# Forzar cancelación sin confirmación
hp job cancel job-abc123-def456 --force
```

Salida esperada:
```
⚠️  Warning: This will cancel job 'job-abc123-def456' and stop all processing.
Are you sure? Type 'yes' to confirm:
yes

🛑 Cancelling job 'job-abc123-def456'...
✅ Job cancelled successfully!

💡 View final status with: hp job status job-abc123-def456
```

## Paso 11: Configuración Multi-Entorno

### Configurar Múltiples Contextos

```bash
# Añadir entorno de producción
hp login https://prod.hodei.io --username operator --password secret --context prod

# Añadir entorno de staging
hp login https://staging.hodei.io --username dev --password dev123 --context staging

# Listar todos los contextos
hp config get-contexts

# Cambiar entre entornos
hp config use-context prod
hp job submit prod-deploy.kts

hp config use-context staging
hp job submit staging-test.kts

hp config use-context default
hp job submit local-dev.kts
```

### Información del Contexto Actual

```bash
# Mostrar contexto activo actual
hp config current-context

# Mostrar información del usuario actual
hp whoami
```

## Monitoreo y Depuración

### Monitoreo de Salud del Sistema

```bash
# Verificación de salud completa
hp health

# Vista general del sistema con métricas
hp status

# Verificar salud de recursos específicos
hp pool list
hp worker list --status offline
hp job list --status failed
```

### Depuración de Trabajos Fallidos

```bash
# Obtener detalles del trabajo
hp job describe job-failed-123

# Verificar logs del trabajo para errores
hp job logs job-failed-123

# Verificar estado del worker
hp worker describe worker-assigned-to-job

# Verificar capacidad del pool
hp pool describe pool-123
```

### Problemas de Conexión del CLI

```bash
# Probar conexión
hp health

# Verificar autenticación actual
hp whoami

# Re-autenticarse si es necesario
hp logout
hp login http://localhost:8080

# Cambiar a contexto diferente
hp config get-contexts
hp config use-context <otro-contexto>
```

## Ejemplo de Flujo Completo

```bash
# Terminal 1: Iniciar orquestador
$ gradle :orchestrator:run
🚀 Starting system bootstrap...
✅ Created user: admin with roles: [ADMIN]
🏗️ Docker environment registered as resource pool
📋 Default worker templates created: 7 templates

# Terminal 2: Usar el CLI HP
$ hp login http://localhost:8080 -u admin -p admin123
✅ Login successful!

$ hp status
📊 System Status
════════════════
🔗 Connected to: http://localhost:8080
✅ Overall Health: healthy
🏊 Resource Pools: 1
👷 Workers: 0 total

$ hp pool list
🏊 Resource Pools:
• auto-discovered-docker (pool-123)
  Type: docker | Status: active

$ hp job submit examples/hello-world.pipeline.kts --name hello-world
✅ Job submitted successfully!
   Job ID: job-abc123-def456

$ hp job logs job-abc123-def456 -f
📄 Following job logs for: job-abc123-def456
[10:35:10] ℹ️ Hello from Hodei Pipelines!
[10:35:12] ✅ Pipeline completed successfully!

$ hp job list
📋 Jobs:
• hello-world (job-abc123-def456)
  Status: completed | Type: pipeline
  Created: 2024-07-04T10:35:00Z
```

## Ejemplo Completo: Ejecutar un Job Pesado con Monitoreo

Esta sección te guía paso a paso para ejecutar un job computacionalmente intensivo que genera muchas trazas, desde la configuración hasta el monitoreo completo.

### Paso 1: Crear Template Personalizada (si no existe)

```bash
# Verificar templates existentes
hp template list

# Si no hay templates adecuadas, crear una customizada
cat > heavy-compute-template.json << 'EOF'
{
  "name": "heavy-compute-worker",
  "description": "Template para tareas computacionalmente intensivas",
  "type": "docker",
  "config": {
    "image": "openjdk:17-jdk-slim",
    "cpus": 2.0,
    "memory": "4GB",
    "environment": {
      "JAVA_OPTS": "-Xmx3g -XX:+UseParallelGC"
    },
    "volumes": [
      "/tmp:/workspace"
    ]
  },
  "capabilities": ["compute-intensive", "java-17"],
  "resourceRequirements": {
    "minCpu": 2,
    "minMemory": "4GB",
    "storage": "10GB"
  }
}
EOF

# Crear la template
hp template create \
  --name heavy-compute-worker \
  --description "Template para computación pesada" \
  --type docker \
  --file heavy-compute-template.json

# Verificar que se creó correctamente
hp template describe heavy-compute-worker
```

### Paso 2: Crear Pool de Recursos Dedicado

```bash
# Crear pool específico para trabajos pesados
hp pool create \
  --name heavy-compute-pool \
  --type docker \
  --max-workers 3 \
  --provider docker

# Verificar estado del pool
hp pool describe heavy-compute-pool
```

### Paso 3: Crear Pipeline Computacionalmente Intensivo

Crear archivo `heavy-pipeline.kts` con tareas que generan muchas trazas:

```kotlin
pipeline {
    metadata {
        name = "heavy-computation-pipeline"
        description = "Pipeline computacionalmente intensivo para demostración"
        version = "1.0.0"
    }
    
    stage("Preparación") {
        script {
            println("🚀 Iniciando pipeline computacionalmente intensivo...")
            val startTime = System.currentTimeMillis()
            env["START_TIME"] = startTime.toString()
            
            println("📊 Configuración del entorno:")
            println("  - Memoria disponible: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
            println("  - Procesadores: ${Runtime.getRuntime().availableProcessors()}")
            println("  - Timestamp inicio: $startTime")
        }
    }
    
    stage("Cálculos Intensivos") {
        parallel {
            stage("Fibonacci Masivo") {
                script {
                    println("🔢 Calculando secuencia Fibonacci hasta 45...")
                    fun fibonacci(n: Int): Long {
                        if (n <= 1) return n.toLong()
                        return fibonacci(n - 1) + fibonacci(n - 2)
                    }
                    
                    for (i in 30..45) {
                        val result = fibonacci(i)
                        println("  Fibonacci($i) = $result")
                        Thread.sleep(500) // Para generar más trazas
                    }
                    println("✅ Fibonacci completado")
                }
            }
            
            stage("Simulación de Procesamiento") {
                script {
                    println("⚙️ Simulando procesamiento de datos pesado...")
                    val data = mutableListOf<Double>()
                    
                    // Generar datos aleatorios
                    repeat(1000000) { i ->
                        data.add(Math.random() * 1000)
                        if (i % 100000 == 0) {
                            println("  Generados ${i + 1} elementos...")
                        }
                    }
                    
                    // Procesar datos
                    println("📈 Procesando datos estadísticos...")
                    val sum = data.sum()
                    val avg = sum / data.size
                    val max = data.maxOrNull() ?: 0.0
                    val min = data.minOrNull() ?: 0.0
                    
                    println("  📊 Resultados estadísticos:")
                    println("    - Total elementos: ${data.size}")
                    println("    - Suma: $sum")
                    println("    - Promedio: $avg")
                    println("    - Máximo: $max")
                    println("    - Mínimo: $min")
                    
                    // Simulación de escritura de archivos
                    println("💾 Simulando escritura de resultados...")
                    repeat(10) { i ->
                        println("  Escribiendo archivo resultado-$i.txt")
                        Thread.sleep(200)
                    }
                    println("✅ Procesamiento de datos completado")
                }
            }
        }
    }
    
    stage("Pruebas de Estrés") {
        script {
            println("🔥 Ejecutando pruebas de estrés del sistema...")
            
            // Simulación de uso intensivo de CPU
            val threads = mutableListOf<Thread>()
            repeat(4) { threadIndex ->
                val thread = Thread {
                    println("  🧵 Hilo $threadIndex iniciado")
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 10000) { // 10 segundos
                        // Operación intensiva de CPU
                        var result = 0L
                        for (i in 1..100000) {
                            result += i * i
                        }
                        if ((System.currentTimeMillis() - startTime) % 1000 < 50) {
                            println("    Hilo $threadIndex - Progreso: ${(System.currentTimeMillis() - startTime) / 1000}s")
                        }
                    }
                    println("  ✅ Hilo $threadIndex completado")
                }
                threads.add(thread)
                thread.start()
            }
            
            // Esperar que terminen todos los hilos
            threads.forEach { it.join() }
            println("🎯 Pruebas de estrés completadas")
        }
    }
    
    stage("Finalización") {
        script {
            val endTime = System.currentTimeMillis()
            val startTime = env["START_TIME"]?.toLong() ?: endTime
            val duration = endTime - startTime
            
            println("🏁 Pipeline completado exitosamente!")
            println("📈 Estadísticas de ejecución:")
            println("  - Tiempo total: ${duration / 1000}s")
            println("  - Timestamp fin: $endTime")
            println("  - Memoria utilizada: ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024} MB")
            
            // Generar resumen final
            println("📋 Resumen de tareas ejecutadas:")
            println("  ✅ Cálculos Fibonacci (30-45)")
            println("  ✅ Procesamiento de 1M elementos")
            println("  ✅ Pruebas de estrés multi-hilo")
            println("  ✅ Simulación de E/O de archivos")
            
            env["EXECUTION_SUMMARY"] = "SUCCESS - Duration: ${duration}ms"
        }
    }
}
```

### Paso 4: Ejecutar el Job y Monitorear en Tiempo Real

```bash
# Enviar el job con configuración específica
hp job submit heavy-pipeline.kts \
  --name "heavy-computation-demo" \
  --priority high \
  --pool heavy-compute-pool \
  --timeout 600

# El comando anterior devolverá algo como:
# ✅ Job submitted successfully!
# Job ID: job-heavy-abc123-def456
# Status: queued

# Capturar el Job ID para el monitoreo
JOB_ID="job-heavy-abc123-def456"  # Reemplaza con el ID real

# Monitorear estado en tiempo real
echo "🔍 Monitoreando job: $JOB_ID"
hp job status $JOB_ID

# Seguir logs en tiempo real (en una terminal separada)
hp job logs $JOB_ID --follow
```

### Paso 5: Monitoreo Avanzado (Terminal separada)

```bash
# Terminal 2: Monitoreo continuo del estado del job
while true; do
    clear
    echo "📊 Estado del Job: $JOB_ID"
    echo "=================================="
    hp job status $JOB_ID
    echo ""
    echo "🏊 Estado del Pool:"
    hp pool describe heavy-compute-pool
    echo ""
    echo "👷 Workers activos:"
    hp worker list --pool heavy-compute-pool
    echo ""
    echo "🕐 $(date) - Actualizando en 5s..."
    sleep 5
done
```

### Paso 6: Análisis Post-Ejecución

```bash
# Una vez que el job termine, obtener información detallada
hp job describe $JOB_ID

# Obtener logs completos para análisis
hp job logs $JOB_ID > heavy-job-logs.txt

# Verificar métricas del worker que ejecutó el job
WORKER_ID=$(hp job describe $JOB_ID | grep "Worker:" | awk '{print $2}')
hp worker describe $WORKER_ID

# Limpiar recursos si es necesario
hp job list --status completed
```

### Salida Esperada del Monitoreo

Durante la ejecución verás trazas como:

```
📄 Following job logs for: job-heavy-abc123-def456
Press Ctrl+C to stop...
─────────────────────────────────────────
[13:45:01] 🚀 Iniciando pipeline computacionalmente intensivo...
[13:45:01] 📊 Configuración del entorno:
[13:45:01]   - Memoria disponible: 3584 MB
[13:45:01]   - Procesadores: 4
[13:45:02] 🔢 Calculando secuencia Fibonacci hasta 45...
[13:45:02] ⚙️ Simulando procesamiento de datos pesado...
[13:45:03]   Fibonacci(30) = 832040
[13:45:03]   Generados 100001 elementos...
[13:45:04]   Fibonacci(31) = 1346269
[13:45:05]   Generados 200001 elementos...
[13:45:06]   Fibonacci(32) = 2178309
[13:45:07]   📈 Procesando datos estadísticos...
[13:45:08]   📊 Resultados estadísticos:
[13:45:08]     - Total elementos: 1000000
[13:45:08]     - Suma: 499847293.45
[13:45:09] 🔥 Ejecutando pruebas de estrés del sistema...
[13:45:09]   🧵 Hilo 0 iniciado
[13:45:09]   🧵 Hilo 1 iniciado
[13:45:10]     Hilo 0 - Progreso: 1s
[13:45:11]     Hilo 1 - Progreso: 2s
[13:45:19]   ✅ Hilo 0 completado
[13:45:20] 🏁 Pipeline completado exitosamente!
[13:45:20] 📈 Estadísticas de ejecución:
[13:45:20]   - Tiempo total: 19s
[13:45:20]   ✅ Cálculos Fibonacci (30-45)
```

Este ejemplo te permite ver:
- **Creación de recursos** (templates y pools)
- **Ejecución de job pesado** con múltiples stages paralelos
- **Monitoreo en tiempo real** con logs detallados
- **Análisis post-ejecución** de métricas y resultados

## Casos de Uso Avanzados

### Pipeline Personalizado con Parámetros

Crear un archivo de pipeline parametrizado `deploy.pipeline.kts`:
```kotlin
pipeline {
    stage("Deploy") {
        script {
            val env = env("TARGET_ENV") ?: "development"
            val version = env("VERSION") ?: "latest"
            
            println("Deploying version $version to $env environment")
            sh("docker deploy --env $env --version $version")
        }
    }
}
```

Enviar con parámetros:
```bash
# Enviar con variables de entorno
hp job submit deploy.pipeline.kts \
  --name "deploy-v1.2.3" \
  --priority high \
  --timeout 600
```

### Gestión de Trabajos en Lote

```bash
# Enviar múltiples trabajos
for i in {1..5}; do
  hp job submit test-job.kts --name "test-job-$i"
done

# Monitorear todos los trabajos
hp job list --status running

# Cancelar todos los trabajos en ejecución (si es necesario)
hp job list --status running | grep job- | awk '{print $2}' | xargs -I {} hp job cancel {} --force
```

### Escalado de Pool de Recursos

```bash
# Crear pool de alto rendimiento
hp pool create \
  --name performance-pool \
  --type docker \
  --max-workers 20 \
  --provider docker

# Enviar trabajos a pool específico
hp job submit heavy-computation.kts \
  --name "heavy-task" \
  --pool performance-pool \
  --priority high
```

## Solución de Problemas

### Problemas Comunes y Soluciones

#### Problemas de Conexión del CLI
```bash
# Verificar estado del orquestador
curl http://localhost:8080/v1/health

# Verificar instalación del CLI
hp version

# Re-autenticarse
hp logout && hp login http://localhost:8080
```

#### Problemas de Ejecución de Trabajos
```bash
# Verificar recursos del sistema
hp status

# Verificar disponibilidad del pool
hp pool list

# Verificar estado de workers
hp worker list

# Revisar detalles del trabajo
hp job describe <job-id>
hp job logs <job-id>
```

#### Problemas de Integración con Docker
```bash
# Verificar que Docker esté ejecutándose
docker version
docker ps

# Verificar permisos de Docker (Linux)
sudo usermod -aG docker $USER
# (cerrar sesión y volver a iniciar)

# Probar conectividad de Docker
docker run hello-world
```

### Obtener Ayuda

```bash
# Ayuda del CLI
hp --help
hp job --help
hp pool create --help

# Verificar versión del CLI
hp version

# Vista general del estado del sistema
hp status
```

## Próximos Pasos

1. **Explorar Funciones Avanzadas**: Prueba los comandos describe, acceso shell y modos dry-run
2. **Crear Pipelines Personalizados**: Desarrolla pipelines específicos para tu flujo de trabajo
3. **Configurar Multi-Entorno**: Configura contextos de staging y producción
4. **Integrar con CI/CD**: Usa la API REST para despliegues automatizados
5. **Escalar tu Configuración**: Crea múltiples pools y plantillas para diferentes cargas de trabajo

## Referencias

- [Referencia Completa del CLI](./CLI_REFERENCE_HP.md) - Documentación completa de comandos
- [Roadmap del CLI](./CLI_ROADMAP.md) - Comparación de funciones y roadmap
- [Guía del Pipeline DSL](./PIPELINE_DSL.md) - Guía de desarrollo de pipelines
- [Vista General de Arquitectura](./ARCHITECTURE.md) - Detalles de arquitectura del sistema

---

**🎉 ¡Felicitaciones!** Ahora tienes una configuración completamente funcional de Hodei Pipelines con capacidades CLI de nivel empresarial. El CLI `hp` proporciona más de 35 comandos para la gestión completa de orquestación de pipelines.