# Guía Rápida: Ejecutar Job con Docker en Hodei Pipelines

Esta guía te muestra cómo levantar el orquestador y ejecutar un job usando Docker en tu máquina local, recibiendo toda la salida por consola.

## Prerrequisitos

- Java 17+ instalado
- Docker instalado y funcionando
- Gradle instalado (no uses gradlew según configuración del proyecto)

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
java -jar orchestrator/build/libs/orchestrator-*.jar --mode=orchestrator

# El orquestador se iniciará en http://localhost:8080
```

El orquestador expondrá:
- REST API en puerto 8080
- gRPC server en puerto 9090 (para comunicación con workers)

## Paso 3: Verificar el Estado del Orquestador

```bash
# Verificar health
curl http://localhost:8080/health

# Respuesta esperada:
# {
#   "status": "healthy",
#   "checks": {
#     "database": "ok",
#     "grpc": "ok"
#   }
# }
```

## Paso 4: Descubrir y Configurar Docker

Usar el CLI de Hodei para descubrir el entorno Docker local:

```bash
# Compilar el CLI si no está compilado
gradle :orchestrator:installDist

# Descubrir Docker
./orchestrator/build/install/orchestrator/bin/hodei docker discover

# Salida esperada:
# 🔍 Discovering Docker environment...
# ✅ Docker discovered: version 28.2.2
# 💻 CPU: 8 cores available
# 🧮 Memory: 16GB total
# 📊 Optimal configuration:
#    - Max workers: 4
#    - Memory per worker: 2GB
#    - CPU per worker: 1.5 cores
```

## Paso 5: Registrar Pool de Recursos Docker

```bash
# Crear un pool de recursos Docker
./orchestrator/build/install/orchestrator/bin/hodei pool create \
  --name "local-docker" \
  --type docker \
  --max-workers 2

# Verificar el pool
./orchestrator/build/install/orchestrator/bin/hodei pool list
```

## Paso 6: Crear Templates de Worker (Opcional)

Los templates ya vienen predefinidos, pero puedes crear uno personalizado:

```bash
# Listar templates disponibles
./orchestrator/build/install/orchestrator/bin/hodei template list

# Crear template personalizado
./orchestrator/build/install/orchestrator/bin/hodei template create \
  --name "my-docker-worker" \
  --type docker \
  --image "hodei/worker:latest" \
  --cpu "500m" \
  --memory "1Gi"
```

## Paso 7: Ejecutar un Job con el Pipeline DSL

### Opción A: Ejecución Local (sin orquestador)

```bash
# Compilar el CLI del pipeline DSL
gradle :pipeline-dsl:pipeline-cli:installDist

# Ejecutar el pipeline de ejemplo localmente
./pipeline-dsl/pipeline-cli/build/install/pipeline-cli/bin/pipeline-dsl execute \
  examples/hello-world.pipeline.kts \
  --verbose

# La salida se mostrará directamente en consola
```

### Opción B: Ejecución Remota (usando el orquestador)

```bash
# Ejecutar el pipeline a través del orquestador
./pipeline-dsl/pipeline-cli/build/install/pipeline-cli/bin/pipeline-dsl execute \
  examples/hello-world.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool local-docker \
  --follow \
  --verbose

# Salida esperada:
# 🚀 Executing pipeline: hello-world.pipeline.kts
# 📋 Job ID: abc123...
# 🌐 Remote execution via orchestrator: http://localhost:8080
# 🏊 Resource Pool: local-docker
# 
# ✅ Connected to orchestrator (healthy)
# 📦 Version: 1.0.0
# 
# 📤 Submitting job to orchestrator...
# ✅ Job submitted successfully!
# 🆔 Remote Job ID: job-xyz789
# 📊 Status: queued
# 
# 👀 Following job execution...
# Press Ctrl+C to stop following (job will continue running)
# 
# 📊 Status: running
# 🔧 Worker: docker-worker-001
# 
# [LOGS EN TIEMPO REAL DEL PIPELINE]
# 🔍 Checking environment...
# Greeting: Hello from Hodei Pipelines!
# Build Number: 1.0.0
# ...
# 🎉 Pipeline completed successfully!
# 
# 🏁 Execution completed!
# 📊 Final Status: completed
# ⏱️ Duration: 15432ms
```

## Paso 8: Iniciar un Worker Docker Manualmente (Opcional)

Si quieres ver el worker en acción, puedes iniciarlo manualmente:

```bash
# Opción 1: Usando Docker directamente
docker run -it --rm \
  --name hodei-worker-001 \
  -e HODEI_ORCHESTRATOR_HOST=host.docker.internal \
  -e HODEI_ORCHESTRATOR_PORT=9090 \
  -e WORKER_ID=manual-worker-001 \
  hodei/worker:latest

# Opción 2: Usando el CLI
./orchestrator/build/install/orchestrator/bin/hodei docker worker start \
  --pool local-docker \
  --count 1
```

## Monitoreo y Depuración

### Ver Logs del Orquestador
```bash
# Los logs se muestran en la consola donde iniciaste el orquestador
# También puedes configurar logging en application.conf
```

### Ver Estado de Jobs
```bash
# Listar jobs
curl http://localhost:8080/jobs

# Ver detalles de un job específico
curl http://localhost:8080/jobs/{job-id}

# Ver logs de ejecución
curl http://localhost:8080/executions/{execution-id}/logs
```

### Ver Workers Activos
```bash
# Usando el CLI
./orchestrator/build/install/orchestrator/bin/hodei docker status

# Usando la API
curl http://localhost:8080/workers
```

## Solución de Problemas

### El orquestador no inicia
- Verifica que el puerto 8080 no esté en uso
- Revisa los logs para errores de configuración
- Asegúrate de tener Java 17+

### Docker no es detectado
- Verifica que Docker esté instalado: `docker --version`
- Asegúrate de que el daemon esté corriendo: `docker ps`
- En Linux, verifica permisos: `sudo usermod -aG docker $USER`

### El worker no se conecta
- Verifica que el orquestador esté corriendo
- Revisa la conectividad de red (especialmente en Docker)
- Usa `host.docker.internal` en lugar de `localhost` desde contenedores

### No veo los logs en tiempo real
- Asegúrate de usar la flag `--follow` o `-f`
- Verifica que el WebSocket/SSE esté funcionando
- Revisa si hay proxies o firewalls bloqueando

## Ejemplo Completo de Sesión

```bash
# Terminal 1: Iniciar orquestador
$ gradle :orchestrator:run
[INFO] Starting Hodei Pipelines Orchestrator...
[INFO] REST API listening on http://localhost:8080
[INFO] gRPC server listening on port 9090

# Terminal 2: Ejecutar comandos
$ ./orchestrator/build/install/orchestrator/bin/hodei docker discover
$ ./orchestrator/build/install/orchestrator/bin/hodei pool create --name local-docker --type docker
$ ./pipeline-dsl/pipeline-cli/build/install/pipeline-cli/bin/pipeline-dsl execute \
    examples/hello-world.pipeline.kts \
    --orchestrator http://localhost:8080 \
    --pool local-docker \
    --follow

# Ver toda la salida del pipeline en tiempo real
```

## Próximos Pasos

1. **Personalizar pipelines**: Modifica `hello-world.pipeline.kts` o crea tus propios pipelines
2. **Configurar templates**: Define templates específicos para tus necesidades
3. **Escalar workers**: Aumenta el número de workers para ejecución paralela
4. **Integrar con CI/CD**: Usa la API REST para integrar con sistemas existentes

## Referencias

- [Documentación del Pipeline DSL](./PIPELINE_DSL.es.md)
- [Referencia de la API REST](./API_REFERENCE.es.md)
- [Guía de Templates de Worker](./WORKER_TEMPLATES.es.md)
- [Visión General de la Arquitectura](./ARCHITECTURE.es.md)