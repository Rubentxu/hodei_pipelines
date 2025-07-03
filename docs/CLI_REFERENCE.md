# Hodei Pipelines CLI Reference

Complete reference guide for all CLI commands available in Hodei Pipelines.

> **Note**: This document provides an overview of all CLI tools. For detailed `hp` CLI reference, see [CLI_REFERENCE_HP.md](./CLI_REFERENCE_HP.md).

## Table of Contents

1. [Hodei CLI Client (`hp`)](#hodei-cli-client-hp)
2. [Pipeline DSL CLI](#pipeline-dsl-cli)
3. [Migración desde comandos legacy](#migración-desde-comandos-legacy)

---

## Hodei CLI Client (`hp`)

The Hodei Pipelines CLI (`hp`) is a command-line client for interacting with the Hodei orchestrator. Similar to `kubectl` or `oc`, it provides a complete interface for managing distributed job execution, resource pools, workers, and templates.

> **📖 For complete `hp` CLI documentation, see [CLI_REFERENCE_HP.md](./CLI_REFERENCE_HP.md)**

### Quick Overview

The `hp` CLI provides these main command groups:

- **Authentication**: `login`, `logout`, `whoami`
- **Resource Management**: `pool list/create/delete`, `worker list/status`, `template list/create/show`
- **Job Management**: `job list/submit/status/logs/cancel`
- **System**: `health`, `version`, `status`
- **Configuration**: `config get-contexts/use-context/current-context`
- **Docker Integration**: `docker discover/status`

### Quick Start

```bash
# 1. Login to orchestrator
hp login http://localhost:8080 --username admin --password admin123

# 2. Check status
hp health
hp whoami

# 3. List resources
hp pool list
hp job list

# 4. Submit a job
hp job submit examples/hello-world.pipeline.kts --name my-job

# 5. Monitor job
hp job status <job-id>
hp job logs <job-id> --follow
```

### Default Users

The orchestrator automatically creates these default users:

| Username | Password | Roles | Description |
|----------|----------|-------|-------------|
| `admin` | `admin123` | ADMIN | Full system access |
| `user` | `user123` | USER | Standard user access |
| `moderator` | `mod123` | MODERATOR | Elevated permissions |

---

## Pipeline DSL CLI

CLI for compiling, validating, and executing pipeline scripts.

### Global Options

```bash
pipeline-dsl [OPTIONS] COMMAND [ARGS]...
```

**Options:**
- `--help` : Show help message

### Execute Command

Executes a pipeline script locally or remotely.

```bash
pipeline-dsl execute PIPELINE_FILE [OPTIONS]
```

**Arguments:**
- `PIPELINE_FILE` : Path to the .pipeline.kts file (required)

**Options:**
- `--job-id ID` : Job ID for execution (auto-generated if not provided)
- `--worker-id ID` : Worker ID for execution (default: `cli-worker-{timestamp}`)
- `--orchestrator, --remote URL` : Orchestrator URL for remote execution
- `--pool POOL_ID` : Resource pool ID for remote execution
- `--follow, -f` : Follow job execution and stream real-time output (default: true)
- `--timeout SECONDS` : Execution timeout in seconds
- `--verbose, -v` : Enable verbose output
- `--env, -e KEY=VALUE` : Override environment variables (repeatable)
- `--param, -p KEY=VALUE` : Override pipeline parameters (repeatable)

**Examples:**

Local execution:
```bash
pipeline-dsl execute my-pipeline.pipeline.kts --verbose
```

Remote execution:
```bash
pipeline-dsl execute my-pipeline.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool docker-pool \
  --follow \
  --param TARGET_ENV=production \
  --env DEBUG=true
```

### Compile Command

Compiles and validates a pipeline script without executing it.

```bash
pipeline-dsl compile PIPELINE_FILE [OPTIONS]
```

**Arguments:**
- `PIPELINE_FILE` : Path to the .pipeline.kts file (required)

**Options:**
- `--verbose, -v` : Enable verbose output with detailed analysis
- `--output, -o PATH` : Save compiled pipeline to file
- `--format FORMAT` : Output format (json, yaml)

**Example:**
```bash
pipeline-dsl compile my-pipeline.pipeline.kts --verbose
# Output:
# 🔧 Compiling pipeline: my-pipeline.pipeline.kts
# ✅ Compilation successful!
# 
# 📋 Pipeline: My Pipeline
# 📄 Description: Example pipeline
# 🏗️ Stages: 4
# 📦 Total steps: 12
# ⏱️ Compilation time: 234ms
```

### Validate Command

Validates pipeline script syntax without full compilation.

```bash
pipeline-dsl validate PIPELINE_FILE [OPTIONS]
```

**Arguments:**
- `PIPELINE_FILE` : Path to the .pipeline.kts file (required)

**Options:**
- `--strict` : Enable strict validation mode
- `--warnings` : Show validation warnings

**Example:**
```bash
pipeline-dsl validate my-pipeline.pipeline.kts
# Output:
# 🔍 Validating pipeline: my-pipeline.pipeline.kts
# ✅ Validation successful! No syntax errors found.
```

### Info Command

Shows Pipeline DSL information and capabilities.

```bash
pipeline-dsl info [OPTIONS]
```

**Options:**
- `--examples` : Show usage examples
- `--functions` : List available DSL functions

**Example:**
```bash
pipeline-dsl info
# Output:
# 📖 Pipeline DSL Information
# ==================================================
# Version: 1.0.0
# Description: Hodei Pipelines DSL for CI/CD workflows
# 
# 🔧 Supported Step Types:
#   • script - Execute Kotlin code
#   • shell - Run shell commands
#   • docker - Run Docker containers
#   • parallel - Execute steps in parallel
#   • conditional - Conditional execution
# 
# 🚀 Features:
#   ✅ Type-safe pipeline definitions
#   ✅ Real-time output streaming
#   ✅ Event-driven architecture
#   ...
```

## Environment Variables

Both CLIs support configuration through environment variables:

### Hodei CLI Environment Variables

- `HODEI_CONFIG_PATH` : Default configuration file path
- `HODEI_ORCHESTRATOR_URL` : Default orchestrator URL
- `HODEI_DOCKER_HOST` : Docker daemon URL
- `HODEI_LOG_LEVEL` : Default log level
- `HODEI_WORK_DIR` : Default working directory

### Pipeline DSL CLI Environment Variables

- `PIPELINE_DSL_ORCHESTRATOR` : Default orchestrator URL
- `PIPELINE_DSL_POOL` : Default resource pool
- `PIPELINE_DSL_TIMEOUT` : Default execution timeout
- `PIPELINE_DSL_VERBOSE` : Enable verbose mode by default

## Configuration Files

### Hodei Configuration (application.conf)

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

### Pipeline Configuration (.hodei.yaml)

```yaml
# Project-specific pipeline configuration
defaults:
  orchestrator: http://orchestrator.example.com:8080
  pool: production-docker
  timeout: 3600
  
parameters:
  environment: production
  notifications: true
```

## Exit Codes

Both CLIs use standard exit codes:

- `0` : Success
- `1` : General error
- `2` : Misuse of shell command
- `126` : Command invoked cannot execute
- `127` : Command not found
- `130` : Script terminated by Ctrl+C

## Examples

### Complete Workflow Example

```bash
# 1. Start orchestrator
java -jar orchestrator-all.jar

# 2. Login to orchestrator (in another terminal)
hp login http://localhost:8080 --username admin --password admin123

# 3. Check system status (Docker discovery happens automatically)
hp health
hp pool list
hp template list

# 4. Execute pipeline
pipeline-dsl execute examples/hello-world.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool docker-pool \
  --follow

# 5. Or submit via hp CLI
hp job submit examples/hello-world.pipeline.kts --name my-job --wait

# 6. Check status
hp pool list
hp worker list
hp job list
```

## Troubleshooting

### Debug Mode

Enable debug output for troubleshooting:

```bash
# hp CLI with verbose output
hp --verbose pool list

# Pipeline DSL CLI  
pipeline-dsl execute pipeline.kts --verbose

# With environment variable
export HODEI_LOG_LEVEL=debug
java -jar orchestrator-all.jar
```

### Common Issues

1. **Connection refused**: Check orchestrator is running with `java -jar orchestrator-all.jar`
2. **Authentication failed**: Use default credentials (admin/admin123) or check login
3. **Docker not found**: Verify Docker installation and permissions
4. **Pipeline compilation error**: Use `validate` command first
5. **Worker not connecting**: Check network connectivity and firewall

---

## Migración desde comandos legacy

Si vienes de versiones anteriores de Hodei que usaban el comando `hodei`, aquí tienes la migración:

### Cambios principales

1. **CLI separado**: El CLI ahora es un binario independiente (`hp`) separado del orchestrator
2. **Bootstrap automático**: El orchestrator se auto-configura al arranque
3. **Descubrimiento automático**: Docker se descubre automáticamente al arrancar
4. **Usuarios por defecto**: Se crean automáticamente usuarios admin/user/moderator

### Comandos migrados

| Comando legacy | Nuevo comando `hp` | Notas |
|---|---|---|
| `hodei server start` | `java -jar orchestrator-all.jar` | Ejecutar orchestrator directamente |
| `hodei docker discover` | N/A | Automático al arrancar |
| `hodei pool list` | `hp pool list` | Requiere login previo |
| `hodei template list` | `hp template list` | Requiere login previo |
| `hodei health` | `hp health` | Requiere login previo |

### Flujo de migración

```bash
# Antes (legacy):
hodei server start --detach
hodei docker discover
hodei pool list

# Ahora (nuevo):
java -jar orchestrator-all.jar &  # En background
hp login http://localhost:8080 --username admin --password admin123
hp pool list  # Docker ya descubierto automáticamente
```

## See Also

- [Complete HP CLI Reference](./CLI_REFERENCE_HP.md)
- [Quick Start Guide](./QUICK_START_DOCKER.md)
- [Pipeline DSL Reference](./PIPELINE_DSL.md)
- [Architecture Overview](./ARCHITECTURE.md)
- [API Reference](./API_REFERENCE.md)