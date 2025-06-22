# 🚀 Hodei Pipelines - Deployment Guide

## 📋 Overview

Este documento describe cómo desplegar y probar el sistema completo de Hodei Pipelines, incluyendo servidor y workers.

## 🏗️ Arquitectura del Sistema

```
┌─────────────────┐     ┌─────────────────┐
│   gRPC Client   │────▶│  Hodei Server   │
│  (grpcurl, etc) │     │    (Port 9090)  │
└─────────────────┘     └─────────────────┘
                                 │
                                 │ gRPC
                                 ▼
                        ┌─────────────────┐
                        │ Pipeline Worker │
                        │  (Auto-register)│
                        └─────────────────┘
```

## 🚀 Opciones de Despliegue

### 1. **Desarrollo Local (Recomendado para MVP)**

#### Requisitos
- Java 17+
- Gradle 8.4+
- grpcurl (para testing)

#### Iniciar el Sistema

**Terminal 1 - Servidor:**
```bash
# Compilar y ejecutar servidor
./gradlew :backend:application:run

# O con puerto personalizado
./gradlew :backend:application:run --args="9091"
```

**Terminal 2 - Worker:**
```bash
# Compilar y ejecutar worker
./gradlew :worker:application:run

# O con configuración personalizada
./gradlew :worker:application:run --args="--worker-name 'Dev Worker' --server-port 9091"
```

### 2. **Docker Compose (Producción Local)**

#### Crear Dockerfiles

**Server Dockerfile:**
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app
COPY backend/application/build/libs/*-all.jar app.jar

EXPOSE 9090
CMD ["java", "-jar", "app.jar"]
```

**Worker Dockerfile:**
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app
COPY worker/application/build/libs/*-all.jar app.jar

ENV WORKER_NAME="Docker Worker"
ENV SERVER_HOST="hodei-server"
ENV MAX_CONCURRENT_JOBS="3"

CMD ["java", "-jar", "app.jar"]
```

**docker-compose.yml:**
```yaml
version: '3.8'

services:
  hodei-server:
    build:
      context: .
      dockerfile: Dockerfile.server
    ports:
      - "9090:9090"
    environment:
      - SERVER_PORT=9090
    healthcheck:
      test: ["CMD", "grpcurl", "-plaintext", "localhost:9090", "list"]
      interval: 30s
      timeout: 10s
      retries: 3

  hodei-worker-1:
    build:
      context: .
      dockerfile: Dockerfile.worker
    depends_on:
      - hodei-server
    environment:
      - WORKER_NAME=Worker-1
      - SERVER_HOST=hodei-server
      - MAX_CONCURRENT_JOBS=3
    
  hodei-worker-2:
    build:
      context: .
      dockerfile: Dockerfile.worker
    depends_on:
      - hodei-server
    environment:
      - WORKER_NAME=Worker-2
      - SERVER_HOST=hodei-server
      - MAX_CONCURRENT_JOBS=5
```

#### Comandos Docker Compose

```bash
# Construir y ejecutar
docker-compose up --build

# Escalar workers
docker-compose up --scale hodei-worker-1=3

# Ver logs
docker-compose logs -f hodei-server
docker-compose logs -f hodei-worker-1
```

### 3. **Kubernetes con Helm (Producción)**

#### Estructura del Chart

```
hodei-pipelines/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── server-deployment.yaml
│   ├── server-service.yaml
│   ├── worker-deployment.yaml
│   └── configmap.yaml
```

**Chart.yaml:**
```yaml
apiVersion: v2
name: hodei-pipelines
description: Distributed CI/CD Pipeline System
version: 0.1.0
appVersion: "1.0.0"
```

**values.yaml:**
```yaml
server:
  image: hodei/server:latest
  replicaCount: 1
  port: 9090
  resources:
    requests:
      memory: "512Mi"
      cpu: "500m"
    limits:
      memory: "1Gi"
      cpu: "1000m"

worker:
  image: hodei/worker:latest
  replicaCount: 3
  maxConcurrentJobs: 5
  resources:
    requests:
      memory: "256Mi"
      cpu: "250m"
    limits:
      memory: "512Mi"
      cpu: "500m"

service:
  type: ClusterIP
  port: 9090
```

**server-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "hodei-pipelines.fullname" . }}-server
spec:
  replicas: {{ .Values.server.replicaCount }}
  selector:
    matchLabels:
      app: hodei-server
  template:
    metadata:
      labels:
        app: hodei-server
    spec:
      containers:
      - name: server
        image: {{ .Values.server.image }}
        ports:
        - containerPort: {{ .Values.server.port }}
        env:
        - name: SERVER_PORT
          value: "{{ .Values.server.port }}"
        resources:
          {{- toYaml .Values.server.resources | nindent 10 }}
```

## 🧪 Testing del Sistema

### 1. **Instalación de grpcurl**

```bash
# macOS
brew install grpcurl

# Linux
wget https://github.com/fullstorydev/grpcurl/releases/download/v1.8.7/grpcurl_1.8.7_linux_x86_64.tar.gz
tar -xvf grpcurl_1.8.7_linux_x86_64.tar.gz
sudo mv grpcurl /usr/local/bin/

# Docker
docker run --rm -it fullstorydev/grpcurl -help
```

### 2. **Testing de Servicios gRPC**

#### Listar Servicios Disponibles
```bash
grpcurl -plaintext localhost:9090 list
```

#### Listar Workers Registrados
```bash
grpcurl -plaintext localhost:9090 \
  dev.rubentxu.hodei.pipelines.proto.v1.WorkerManagementService/ListWorkers
```

#### Obtener Info de Worker Específico
```bash
grpcurl -plaintext \
  -d '{"value": "worker-12345"}' \
  localhost:9090 \
  dev.rubentxu.hodei.pipelines.proto.v1.WorkerManagementService/GetWorkerInfo
```

#### Ejecutar Job Simple
```bash
grpcurl -plaintext \
  -d '{
    "init_request": {
      "job_definition": {
        "id": {"value": "test-job-1"},
        "name": "Test Job",
        "command": ["echo", "Hello World"],
        "working_directory": "/tmp",
        "environment": {
          "SCRIPT_CONTENT": "tasks.register(\"main\") { doLast { println(\"Hello from Kotlin Script!\") } }; tasks.getByName(\"main\").execute()"
        }
      }
    }
  }' \
  localhost:9090 \
  dev.rubentxu.hodei.pipelines.proto.v1.JobExecutorService/ExecuteJob
```

### 3. **Testing con Cliente Personalizado**

**Crear cliente de prueba simple:**
```kotlin
// TestClient.kt
import io.grpc.ManagedChannelBuilder
import dev.rubentxu.hodei.pipelines.proto.*

fun main() {
    val channel = ManagedChannelBuilder
        .forAddress("localhost", 9090)
        .usePlaintext()
        .build()
    
    val workerStub = WorkerManagementServiceGrpcKt
        .WorkerManagementServiceCoroutineStub(channel)
    
    // Listar workers
    val workers = workerStub.listWorkers(Empty.getDefaultInstance())
    workers.collect { worker ->
        println("Worker: ${worker.name} (${worker.id.value})")
    }
    
    channel.shutdown()
}
```

### 4. **Scripts de Testing Automatizado**

**test-pipeline.sh:**
```bash
#!/bin/bash

SERVER_HOST=${1:-localhost}
SERVER_PORT=${2:-9090}

echo "🧪 Testing Hodei Pipelines System..."
echo "Server: $SERVER_HOST:$SERVER_PORT"

# 1. Check server health
echo "1. Checking server health..."
grpcurl -plaintext $SERVER_HOST:$SERVER_PORT list > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Server is responding"
else
    echo "❌ Server is not responding"
    exit 1
fi

# 2. List workers
echo "2. Listing registered workers..."
WORKERS=$(grpcurl -plaintext $SERVER_HOST:$SERVER_PORT \
    dev.rubentxu.hodei.pipelines.proto.v1.WorkerManagementService/ListWorkers)
echo "$WORKERS"

# 3. Execute test job
echo "3. Executing test job..."
grpcurl -plaintext \
  -d '{
    "init_request": {
      "job_definition": {
        "id": {"value": "test-'$(date +%s)'"},
        "name": "Health Check Job",
        "command": ["echo", "System OK"],
        "working_directory": "/tmp",
        "environment": {
          "SCRIPT_CONTENT": "tasks.register(\"healthcheck\") { doLast { println(\"✅ Pipeline system is working!\") } }; tasks.getByName(\"healthcheck\").execute()"
        }
      }
    }
  }' \
  $SERVER_HOST:$SERVER_PORT \
  dev.rubentxu.hodei.pipelines.proto.v1.JobExecutorService/ExecuteJob

echo "🎉 Test completed!"
```

## 📊 Monitoreo y Observabilidad

### Logs del Sistema
```bash
# Server logs
./gradlew :backend:application:run | grep -E "(INFO|ERROR|WARN)"

# Worker logs
./gradlew :worker:application:run | grep -E "(Worker|Job|Heartbeat)"
```

### Métricas Básicas
- **Server**: Workers registrados, jobs ejecutados, errores de conexión
- **Worker**: Jobs completados, tiempo de ejecución, uso de recursos

## 🚨 Troubleshooting

### Problemas Comunes

1. **Worker no se conecta al servidor**
   ```bash
   # Verificar conectividad
   telnet localhost 9090
   
   # Verificar logs del servidor
   grep "WorkerRegistration" logs/server.log
   ```

2. **Jobs fallan constantemente**
   ```bash
   # Verificar capacidades del worker
   grpcurl -plaintext localhost:9090 \
     dev.rubentxu.hodei.pipelines.proto.v1.WorkerManagementService/ListWorkers
   ```

3. **Problemas de red en Docker**
   ```bash
   # Verificar red entre contenedores
   docker-compose exec hodei-worker-1 ping hodei-server
   ```

## 🎯 Recomendación para MVP

**Para el MVP actual, recomiendo usar Docker Compose** porque:

1. ✅ **Simplicidad**: Fácil de configurar y ejecutar
2. ✅ **Portabilidad**: Funciona igual en dev y producción
3. ✅ **Escalabilidad**: Fácil escalar workers con `--scale`
4. ✅ **Observabilidad**: Logs centralizados y fácil debugging
5. ✅ **Sin overhead**: No necesita Kubernetes para MVP

**Migración futura a Kubernetes** cuando se necesite:
- Auto-scaling de workers basado en carga
- Multi-tenancy y namespace isolation
- Persistent volumes para state
- Service mesh y observabilidad avanzada

## 🔄 Next Steps

1. **Ejecutar el sistema localmente** con Gradle
2. **Crear los Dockerfiles** y docker-compose.yml
3. **Probar con grpcurl** los servicios básicos
4. **Configurar CI/CD** para build de imágenes Docker
5. **Preparar Helm chart** para producción K8s