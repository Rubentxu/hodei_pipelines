# Referencia de CLI de Hodei Pipelines

Guía completa de referencia para todos los comandos CLI disponibles en Hodei Pipelines.

## Tabla de Contenidos

1. [Hodei CLI (Orquestador)](#hodei-cli-orquestador)
   - [Comandos Docker](#comandos-docker)
   - [Comandos de Servidor](#comandos-de-servidor)
   - [Comandos de Pool](#comandos-de-pool)
   - [Comandos de Template](#comandos-de-template)
   - [Comandos Independientes](#comandos-independientes)
2. [Pipeline DSL CLI](#pipeline-dsl-cli)
   - [Comando Execute](#comando-execute)
   - [Comando Compile](#comando-compile)
   - [Comando Validate](#comando-validate)
   - [Comando Info](#comando-info)

---

## Hodei CLI (Orquestador)

El CLI principal del orquestador para gestionar la plataforma Hodei Pipelines.

### Opciones Globales

```bash
hodei [OPCIONES] COMANDO [ARGS]...
```

**Opciones:**
- `--verbose, -v` : Habilitar salida detallada
- `--config, -c RUTA` : Ruta al archivo de configuración (por defecto: `application.conf`)
- `--help` : Mostrar mensaje de ayuda

### Comandos Docker

Comandos para la gestión de workers Docker y descubrimiento del entorno.

#### `hodei docker discover`

Descubre el entorno Docker local y analiza sus capacidades.

```bash
hodei docker discover [OPCIONES]
```

**Opciones:**
- `--json` : Salida en formato JSON
- `--verbose, -v` : Mostrar información detallada
- `--docker-host HOST` : URL del daemon Docker (por defecto: `unix:///var/run/docker.sock`)

**Salida:**
- Versión de Docker y versión de API
- Núcleos de CPU y memoria disponibles
- Configuración óptima de workers
- Advertencias de compatibilidad

**Ejemplo:**
```bash
hodei docker discover
# Salida:
# 🔍 Descubriendo entorno Docker...
# ✅ Versión de Docker: 28.2.2
# 💻 CPU: 8 núcleos
# 🧮 Memoria: 16GB
# 📊 Configuración óptima:
#    - Máx. workers: 4
#    - Memoria por worker: 2GB
```

#### `hodei docker worker`

Gestiona el ciclo de vida de los workers Docker.

```bash
hodei docker worker SUBCOMANDO [OPCIONES]
```

**Subcomandos:**

##### `start`
Inicia workers Docker en un pool de recursos.

```bash
hodei docker worker start [OPCIONES]
```

**Opciones:**
- `--pool, -p ID` : ID del pool de recursos (requerido)
- `--count, -c N` : Número de workers a iniciar (por defecto: 1)
- `--template, -t NOMBRE` : Nombre del template de worker (por defecto: `default-docker-worker`)
- `--detach, -d` : Ejecutar workers en segundo plano
- `--follow, -f` : Seguir logs de workers
- `--env, -e CLAVE=VALOR` : Variables de entorno (repetible)

**Ejemplo:**
```bash
hodei docker worker start --pool docker-local --count 2 --follow
```

##### `stop`
Detiene workers Docker en ejecución.

```bash
hodei docker worker stop [OPCIONES]
```

**Opciones:**
- `--worker, -w ID` : ID específico del worker a detener
- `--pool, -p ID` : Detener todos los workers del pool
- `--all` : Detener todos los workers Docker
- `--force` : Forzar terminación inmediata
- `--timeout SEGUNDOS` : Período de gracia antes de forzar (por defecto: 30)

##### `list`
Lista workers Docker.

```bash
hodei docker worker list [OPCIONES]
```

**Opciones:**
- `--pool, -p ID` : Filtrar por pool de recursos
- `--status ESTADO` : Filtrar por estado (running, stopped, error)
- `--format FORMATO` : Formato de salida (table, json, yaml)

#### `hodei docker status`

Muestra el estado del daemon Docker y los workers.

```bash
hodei docker status [OPCIONES]
```

**Opciones:**
- `--detailed` : Mostrar información detallada del estado
- `--watch, -w` : Monitorear estado continuamente

### Comandos de Servidor

Comandos para gestionar el servidor orquestador.

#### `hodei server start`

Inicia el servidor orquestador.

```bash
hodei server start [OPCIONES]
```

**Opciones:**
- `--port, -p PUERTO` : Puerto de la API REST (por defecto: 8080)
- `--grpc-port PUERTO` : Puerto del servidor gRPC (por defecto: 9090)
- `--host, -h HOST` : Dirección de enlace (por defecto: 0.0.0.0)
- `--config, -c RUTA` : Ruta del archivo de configuración
- `--detach, -d` : Ejecutar servidor en segundo plano
- `--log-level NIVEL` : Nivel de logging (debug, info, warn, error)

**Ejemplo:**
```bash
hodei server start --port 8080 --grpc-port 9090 --log-level info
```

#### `hodei server stop`

Detiene el servidor orquestador.

```bash
hodei server stop [OPCIONES]
```

**Opciones:**
- `--force` : Forzar apagado inmediato
- `--timeout SEGUNDOS` : Tiempo de espera para apagado ordenado (por defecto: 30)

#### `hodei server status`

Muestra el estado del servidor orquestador.

```bash
hodei server status [OPCIONES]
```

**Opciones:**
- `--format FORMATO` : Formato de salida (text, json)
- `--health` : Incluir resultados de verificación de salud

### Comandos de Pool

Comandos para gestionar pools de recursos.

#### `hodei pool create`

Crea un nuevo pool de recursos.

```bash
hodei pool create [OPCIONES]
```

**Opciones:**
- `--name, -n NOMBRE` : Nombre del pool (requerido)
- `--type, -t TIPO` : Tipo de pool: docker, kubernetes, vm (requerido)
- `--max-workers N` : Máximo de workers permitidos (por defecto: 10)
- `--description, -d TEXTO` : Descripción del pool
- `--labels, -l CLAVE=VALOR` : Etiquetas del pool (repetible)
- `--config, -c JSON` : Configuración específica del pool como JSON

**Ejemplo:**
```bash
hodei pool create \
  --name produccion-docker \
  --type docker \
  --max-workers 20 \
  --description "Pool Docker de producción" \
  --labels env=prod,equipo=plataforma
```

#### `hodei pool list`

Lista todos los pools de recursos.

```bash
hodei pool list [OPCIONES]
```

**Opciones:**
- `--type TIPO` : Filtrar por tipo de pool
- `--status ESTADO` : Filtrar por estado (active, draining, maintenance)
- `--labels SELECTOR` : Selector de etiquetas (ej. `env=prod`)
- `--format FORMATO` : Formato de salida (table, json, yaml)
- `--sort-by CAMPO` : Ordenar por campo (name, created, workers)

#### `hodei pool delete`

Elimina un pool de recursos.

```bash
hodei pool delete POOL_ID [OPCIONES]
```

**Opciones:**
- `--force` : Forzar eliminación incluso con workers activos
- `--cascade` : Eliminar todos los recursos asociados

#### `hodei pool status`

Muestra el estado detallado de un pool de recursos.

```bash
hodei pool status POOL_ID [OPCIONES]
```

**Opciones:**
- `--watch, -w` : Monitorear estado continuamente
- `--metrics` : Incluir métricas de utilización de recursos

### Comandos de Template

Comandos para gestionar templates de workers.

#### `hodei template create`

Crea un nuevo template de worker.

```bash
hodei template create [OPCIONES]
```

**Opciones:**
- `--name, -n NOMBRE` : Nombre del template (requerido)
- `--type, -t TIPO` : Tipo de worker: docker, kubernetes-pod, vm (requerido)
- `--image, -i IMAGEN` : Imagen de contenedor/VM (requerido)
- `--cpu CPU` : Solicitud de CPU (ej. "500m", "2")
- `--memory MEM` : Solicitud de memoria (ej. "1Gi", "2048Mi")
- `--gpu GPU` : Solicitud de GPU
- `--env, -e CLAVE=VALOR` : Variables de entorno (repetible)
- `--labels, -l CLAVE=VALOR` : Etiquetas (repetible)
- `--command CMD` : Sobrescribir comando por defecto
- `--args ARGS` : Argumentos del comando
- `--from-file RUTA` : Crear desde archivo YAML/JSON

**Ejemplo:**
```bash
hodei template create \
  --name worker-gpu \
  --type docker \
  --image hodei/gpu-worker:latest \
  --cpu 4 \
  --memory 8Gi \
  --gpu 1 \
  --env CUDA_VISIBLE_DEVICES=0
```

#### `hodei template list`

Lista todos los templates de workers.

```bash
hodei template list [OPCIONES]
```

**Opciones:**
- `--type TIPO` : Filtrar por tipo de worker
- `--format FORMATO` : Formato de salida (table, json, yaml)

#### `hodei template show`

Muestra detalles de un template de worker.

```bash
hodei template show NOMBRE_TEMPLATE [OPCIONES]
```

**Opciones:**
- `--format FORMATO` : Formato de salida (yaml, json)
- `--versions` : Mostrar todas las versiones

### Comandos Independientes

#### `hodei health`

Verifica la salud del sistema orquestador.

```bash
hodei health [OPCIONES]
```

**Opciones:**
- `--orchestrator URL` : URL del orquestador (por defecto: http://localhost:8080)
- `--detailed` : Mostrar información detallada de salud
- `--format FORMATO` : Formato de salida (text, json)

#### `hodei version`

Muestra información de versión.

```bash
hodei version [OPCIONES]
```

**Opciones:**
- `--full` : Mostrar detalles completos incluyendo dependencias

#### `hodei worker`

Inicia un worker en modo independiente (sin Docker).

```bash
hodei worker [OPCIONES]
```

**Opciones:**
- `--worker-id ID` : Identificador del worker (auto-generado si no se proporciona)
- `--orchestrator-host HOST` : Hostname del orquestador (por defecto: localhost)
- `--orchestrator-port PUERTO` : Puerto gRPC del orquestador (por defecto: 9090)
- `--work-dir RUTA` : Directorio de trabajo para ejecución de jobs
- `--max-jobs N` : Máximo de jobs concurrentes (por defecto: 1)
- `--heartbeat-interval SEGUNDOS` : Intervalo de heartbeat (por defecto: 30)

**Ejemplo:**
```bash
hodei worker \
  --worker-id worker-manual-001 \
  --orchestrator-host orquestador.ejemplo.com \
  --orchestrator-port 9090
```

---

## Pipeline DSL CLI

CLI para compilar, validar y ejecutar scripts de pipeline.

### Opciones Globales

```bash
pipeline-dsl [OPCIONES] COMANDO [ARGS]...
```

**Opciones:**
- `--help` : Mostrar mensaje de ayuda

### Comando Execute

Ejecuta un script de pipeline local o remotamente.

```bash
pipeline-dsl execute ARCHIVO_PIPELINE [OPCIONES]
```

**Argumentos:**
- `ARCHIVO_PIPELINE` : Ruta al archivo .pipeline.kts (requerido)

**Opciones:**
- `--job-id ID` : ID del job para ejecución (auto-generado si no se proporciona)
- `--worker-id ID` : ID del worker para ejecución (por defecto: `cli-worker-{timestamp}`)
- `--orchestrator, --remote URL` : URL del orquestador para ejecución remota
- `--pool POOL_ID` : ID del pool de recursos para ejecución remota
- `--follow, -f` : Seguir ejecución del job y transmitir salida en tiempo real (por defecto: true)
- `--timeout SEGUNDOS` : Tiempo límite de ejecución en segundos
- `--verbose, -v` : Habilitar salida detallada
- `--env, -e CLAVE=VALOR` : Sobrescribir variables de entorno (repetible)
- `--param, -p CLAVE=VALOR` : Sobrescribir parámetros del pipeline (repetible)

**Ejemplos:**

Ejecución local:
```bash
pipeline-dsl execute mi-pipeline.pipeline.kts --verbose
```

Ejecución remota:
```bash
pipeline-dsl execute mi-pipeline.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool docker-pool \
  --follow \
  --param TARGET_ENV=produccion \
  --env DEBUG=true
```

### Comando Compile

Compila y valida un script de pipeline sin ejecutarlo.

```bash
pipeline-dsl compile ARCHIVO_PIPELINE [OPCIONES]
```

**Argumentos:**
- `ARCHIVO_PIPELINE` : Ruta al archivo .pipeline.kts (requerido)

**Opciones:**
- `--verbose, -v` : Habilitar salida detallada con análisis detallado
- `--output, -o RUTA` : Guardar pipeline compilado en archivo
- `--format FORMATO` : Formato de salida (json, yaml)

**Ejemplo:**
```bash
pipeline-dsl compile mi-pipeline.pipeline.kts --verbose
# Salida:
# 🔧 Compilando pipeline: mi-pipeline.pipeline.kts
# ✅ ¡Compilación exitosa!
# 
# 📋 Pipeline: Mi Pipeline
# 📄 Descripción: Pipeline de ejemplo
# 🏗️ Stages: 4
# 📦 Total de steps: 12
# ⏱️ Tiempo de compilación: 234ms
```

### Comando Validate

Valida la sintaxis del script de pipeline sin compilación completa.

```bash
pipeline-dsl validate ARCHIVO_PIPELINE [OPCIONES]
```

**Argumentos:**
- `ARCHIVO_PIPELINE` : Ruta al archivo .pipeline.kts (requerido)

**Opciones:**
- `--strict` : Habilitar modo de validación estricta
- `--warnings` : Mostrar advertencias de validación

**Ejemplo:**
```bash
pipeline-dsl validate mi-pipeline.pipeline.kts
# Salida:
# 🔍 Validando pipeline: mi-pipeline.pipeline.kts
# ✅ ¡Validación exitosa! No se encontraron errores de sintaxis.
```

### Comando Info

Muestra información y capacidades del Pipeline DSL.

```bash
pipeline-dsl info [OPCIONES]
```

**Opciones:**
- `--examples` : Mostrar ejemplos de uso
- `--functions` : Listar funciones DSL disponibles

**Ejemplo:**
```bash
pipeline-dsl info
# Salida:
# 📖 Información del Pipeline DSL
# ==================================================
# Versión: 1.0.0
# Descripción: DSL de Hodei Pipelines para flujos CI/CD
# 
# 🔧 Tipos de Steps Soportados:
#   • script - Ejecutar código Kotlin
#   • shell - Ejecutar comandos shell
#   • docker - Ejecutar contenedores Docker
#   • parallel - Ejecutar steps en paralelo
#   • conditional - Ejecución condicional
# 
# 🚀 Características:
#   ✅ Definiciones de pipeline type-safe
#   ✅ Streaming de salida en tiempo real
#   ✅ Arquitectura basada en eventos
#   ...
```

## Variables de Entorno

Ambos CLIs soportan configuración a través de variables de entorno:

### Variables de Entorno del Hodei CLI

- `HODEI_CONFIG_PATH` : Ruta del archivo de configuración por defecto
- `HODEI_ORCHESTRATOR_URL` : URL del orquestador por defecto
- `HODEI_DOCKER_HOST` : URL del daemon Docker
- `HODEI_LOG_LEVEL` : Nivel de log por defecto
- `HODEI_WORK_DIR` : Directorio de trabajo por defecto

### Variables de Entorno del Pipeline DSL CLI

- `PIPELINE_DSL_ORCHESTRATOR` : URL del orquestador por defecto
- `PIPELINE_DSL_POOL` : Pool de recursos por defecto
- `PIPELINE_DSL_TIMEOUT` : Tiempo límite de ejecución por defecto
- `PIPELINE_DSL_VERBOSE` : Habilitar modo verbose por defecto

## Archivos de Configuración

### Configuración de Hodei (application.conf)

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
}

hodei {
    grpc {
        port = 9090
    }
    
    docker {
        host = "unix:///var/run/docker.sock"
        maxWorkers = 10
    }
    
    pools {
        default {
            type = "docker"
            maxWorkers = 5
        }
    }
}
```

### Configuración de Pipeline (.hodei.yaml)

```yaml
# Configuración de pipeline específica del proyecto
defaults:
  orchestrator: http://orquestador.ejemplo.com:8080
  pool: produccion-docker
  timeout: 3600
  
parameters:
  environment: produccion
  notifications: true
```

## Códigos de Salida

Ambos CLIs usan códigos de salida estándar:

- `0` : Éxito
- `1` : Error general
- `2` : Mal uso del comando shell
- `126` : El comando invocado no puede ejecutarse
- `127` : Comando no encontrado
- `130` : Script terminado por Ctrl+C

## Ejemplos

### Ejemplo de Flujo Completo

```bash
# 1. Iniciar orquestador
hodei server start --detach

# 2. Descubrir Docker
hodei docker discover

# 3. Crear pool de recursos
hodei pool create --name local --type docker --max-workers 4

# 4. Crear template personalizado
hodei template create \
  --name worker-rapido \
  --type docker \
  --image hodei/worker:latest \
  --cpu 2 \
  --memory 4Gi

# 5. Iniciar workers
hodei docker worker start --pool local --count 2 --template worker-rapido

# 6. Ejecutar pipeline
pipeline-dsl execute examples/hello-world.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool local \
  --follow

# 7. Verificar estado
hodei pool status local
hodei docker worker list --pool local

# 8. Limpieza
hodei docker worker stop --all
hodei pool delete local --force
hodei server stop
```

## Solución de Problemas

### Modo Debug

Habilitar salida de depuración para solución de problemas:

```bash
# Hodei CLI
hodei --verbose docker discover

# Pipeline DSL CLI  
pipeline-dsl execute pipeline.kts --verbose

# Con variable de entorno
export HODEI_LOG_LEVEL=debug
hodei server start
```

### Problemas Comunes

1. **Conexión rechazada**: Verificar que el orquestador esté ejecutándose
2. **Docker no encontrado**: Verificar instalación de Docker y permisos
3. **Error de compilación de pipeline**: Usar comando `validate` primero
4. **Worker no se conecta**: Verificar conectividad de red y firewall

## Ver También

- [Guía de Inicio Rápido](./QUICK_START_DOCKER.es.md)
- [Referencia del Pipeline DSL](./PIPELINE_DSL.es.md)
- [Visión General de la Arquitectura](./ARCHITECTURE.es.md)
- [Referencia de la API](./API_REFERENCE.es.md)