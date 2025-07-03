# Hodei Pipelines CLI Reference (`hp`)

The Hodei Pipelines CLI (`hp`) is a command-line client for interacting with the Hodei orchestrator. Similar to `kubectl` or `oc`, it provides a complete interface for managing distributed job execution, resource pools, workers, and templates.

## Installation

### Prerequisites
- Java 17 or higher
- Access to a running Hodei orchestrator

### Quick Install
```bash
# Extract the CLI distribution
tar -xzf hodei-pipelines-cli.tar.gz
cd hodei-pipelines-cli/bin

# Make the binary executable
chmod +x hp

# Add to PATH (optional)
export PATH=$PATH:$(pwd)
```

## Quick Start

### 1. Login to Orchestrator
```bash
# Login with default admin credentials
hp login http://localhost:8080 --username admin --password admin123

# Login interactively (prompts for credentials)
hp login http://localhost:8080

# Login and save context
hp login http://localhost:8080 --username user --password user123 --context production
```

### 2. Check Status
```bash
# Check orchestrator health
hp health

# Check current user
hp whoami

# Check system status
hp status
```

### 3. List Resources
```bash
# List resource pools
hp pool list

# List jobs
hp job list

# List workers
hp worker list

# List templates
hp template list
```

## Global Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--verbose` | `-v` | Enable verbose output | false |
| `--config` | `-c` | Path to CLI configuration file | `~/.hodei/config` |
| `--context` | | Use specific context from config | current |
| `--help` | `-h` | Show help information | |

## Authentication Commands

### login
Authenticate with the Hodei orchestrator.

```bash
hp login <orchestrator-url> [options]
```

**Arguments:**
- `orchestrator-url`: URL of the Hodei orchestrator (e.g., `http://localhost:8080`)

**Options:**
- `--username, -u`: Username for authentication
- `--password, -p`: Password for authentication  
- `--context`: Save credentials with this context name
- `--insecure`: Skip TLS certificate verification

**Examples:**
```bash
# Basic login
hp login http://localhost:8080 -u admin -p admin123

# Interactive login
hp login http://localhost:8080

# Login with custom context
hp login http://production.hodei.io -u user -p pass --context prod

# Login with insecure connection
hp login http://localhost:8080 --insecure
```

### logout
Logout from the current context.

```bash
hp logout [options]
```

**Options:**
- `--context`: Logout from specific context
- `--all`: Logout from all contexts

**Examples:**
```bash
# Logout from current context
hp logout

# Logout from specific context
hp logout --context production

# Logout from all contexts
hp logout --all
```

### whoami
Display information about the current authenticated user.

```bash
hp whoami
```

**Output:**
```
Username: admin
Email: admin@hodei.local
Roles: [ADMIN]
Context: default
Server: http://localhost:8080
```

## Resource Pool Commands

### pool list
List all available resource pools.

```bash
hp pool list [options]
```

**Options:**
- `--output, -o`: Output format (table, json, yaml)
- `--filter`: Filter pools by type (docker, kubernetes, vm)

**Examples:**
```bash
# List all pools
hp pool list

# List in JSON format
hp pool list -o json

# List only Docker pools
hp pool list --filter docker
```

### pool create
Create a new resource pool.

```bash
hp pool create <name> [options]
```

**Arguments:**
- `name`: Name of the resource pool

**Options:**
- `--type`: Pool type (docker, kubernetes, vm)
- `--max-workers`: Maximum number of workers
- `--config`: Configuration file (JSON/YAML)

**Examples:**
```bash
# Create Docker pool
hp pool create my-docker-pool --type docker --max-workers 5

# Create from config file
hp pool create k8s-pool --config pool-config.yaml
```

### pool delete
Delete a resource pool.

```bash
hp pool delete <name>
```

**Examples:**
```bash
hp pool delete my-docker-pool
```

### pool status
Show detailed status of a resource pool.

```bash
hp pool status <name>
```

**Examples:**
```bash
hp pool status default-docker-pool
```

## Job Commands

### job list
List jobs in the system.

```bash
hp job list [options]
```

**Options:**
- `--status`: Filter by job status (running, completed, failed, pending)
- `--limit`: Limit number of results
- `--output, -o`: Output format (table, json, yaml)

**Examples:**
```bash
# List all jobs
hp job list

# List running jobs
hp job list --status running

# List last 10 jobs
hp job list --limit 10
```

### job submit
Submit a new job for execution.

```bash
hp job submit <pipeline-file> [options]
```

**Arguments:**
- `pipeline-file`: Path to pipeline definition file (.kts)

**Options:**
- `--name`: Job name (auto-generated if not provided)
- `--pool`: Target resource pool
- `--template`: Worker template to use
- `--parameters`: Job parameters (JSON string or @file)
- `--wait`: Wait for job completion
- `--timeout`: Timeout for waiting (e.g., 30m, 1h)

**Examples:**
```bash
# Submit basic job
hp job submit my-pipeline.kts

# Submit with custom name and pool
hp job submit build.kts --name "build-v1.2.3" --pool production

# Submit and wait for completion
hp job submit test.kts --wait --timeout 30m

# Submit with parameters
hp job submit deploy.kts --parameters '{"env": "staging", "version": "1.0.0"}'

# Submit with parameters from file
hp job submit deploy.kts --parameters @params.json
```

### job status
Show detailed status of a job.

```bash
hp job status <job-id>
```

**Examples:**
```bash
hp job status 12345-abcde-67890
```

### job logs
Show logs for a job.

```bash
hp job logs <job-id> [options]
```

**Options:**
- `--follow, -f`: Follow log output
- `--lines`: Number of lines to show
- `--since`: Show logs since timestamp (e.g., 1h, 30m)

**Examples:**
```bash
# Show job logs
hp job logs 12345-abcde-67890

# Follow logs
hp job logs 12345-abcde-67890 -f

# Show last 100 lines
hp job logs 12345-abcde-67890 --lines 100

# Show logs from last hour
hp job logs 12345-abcde-67890 --since 1h
```

### job cancel
Cancel a running job.

```bash
hp job cancel <job-id>
```

**Examples:**
```bash
hp job cancel 12345-abcde-67890
```

## Worker Commands

### worker list
List workers in the system.

```bash
hp worker list [options]
```

**Options:**
- `--pool`: Filter by resource pool
- `--status`: Filter by status (active, idle, busy, failed)
- `--output, -o`: Output format (table, json, yaml)

**Examples:**
```bash
# List all workers
hp worker list

# List workers in specific pool
hp worker list --pool docker-pool

# List only active workers
hp worker list --status active
```

### worker status
Show detailed status of a worker.

```bash
hp worker status <worker-id>
```

**Examples:**
```bash
hp worker status worker-123
```

## Template Commands

### template list
List available worker templates.

```bash
hp template list [options]
```

**Options:**
- `--type`: Filter by template type (docker, kubernetes)
- `--output, -o`: Output format (table, json, yaml)

**Examples:**
```bash
# List all templates
hp template list

# List Docker templates
hp template list --type docker
```

### template create
Create a new worker template.

```bash
hp template create <name> [options]
```

**Arguments:**
- `name`: Template name

**Options:**
- `--file, -f`: Template definition file
- `--type`: Template type (docker, kubernetes)
- `--image`: Container image
- `--cpu`: CPU requirements
- `--memory`: Memory requirements

**Examples:**
```bash
# Create from file
hp template create my-template -f template.yaml

# Create Docker template
hp template create node-template --type docker --image node:18 --cpu 1 --memory 2Gi
```

### template show
Show detailed information about a template.

```bash
hp template show <name>
```

**Examples:**
```bash
hp template show default-docker-worker
```

## Docker Integration Commands

### docker discover
Discover and register local Docker environment as a resource pool.

```bash
hp docker discover [options]
```

**Options:**
- `--name`: Name for the discovered pool
- `--max-workers`: Maximum workers for the pool

**Examples:**
```bash
# Auto-discover Docker
hp docker discover

# Discover with custom settings
hp docker discover --name local-docker --max-workers 10
```

### docker status
Show Docker environment status.

```bash
hp docker status
```

## System Commands

### version
Show CLI and orchestrator version information.

```bash
hp version
```

### health
Check orchestrator health status.

```bash
hp health
```

### status
Show comprehensive system status.

```bash
hp status
```

## Configuration Commands

### config get-contexts
List all configured contexts.

```bash
hp config get-contexts
```

### config use-context
Switch to a different context.

```bash
hp config use-context <context-name>
```

**Examples:**
```bash
hp config use-context production
```

### config current-context
Show the current active context.

```bash
hp config current-context
```

## Configuration File

The CLI stores configuration in `~/.hodei/config` (JSON format):

```json
{
  "current-context": "default",
  "contexts": {
    "default": {
      "server": "http://localhost:8080",
      "user": "admin",
      "token": "eyJhbGciOiJIUzI1NiIs...",
      "insecure": false
    },
    "production": {
      "server": "https://hodei.example.com",
      "user": "operator",
      "token": "eyJhbGciOiJIUzI1NiIs...",
      "insecure": false
    }
  }
}
```

## Output Formats

Most commands support multiple output formats:

### Table (Default)
```bash
hp pool list
```
```
NAME                TYPE     WORKERS  STATUS
default-docker-pool docker   2/5      healthy
kubernetes-pool     k8s      0/10     pending
```

### JSON
```bash
hp pool list -o json
```
```json
[
  {
    "name": "default-docker-pool",
    "type": "docker",
    "maxWorkers": 5,
    "activeWorkers": 2,
    "status": "healthy"
  }
]
```

### YAML
```bash
hp pool list -o yaml
```
```yaml
- name: default-docker-pool
  type: docker
  maxWorkers: 5
  activeWorkers: 2
  status: healthy
```

## Examples

### Complete Workflow Example

```bash
# 1. Login to orchestrator
hp login http://localhost:8080 -u admin -p admin123

# 2. Check system status
hp health
hp status

# 3. List available resources
hp pool list
hp template list

# 4. Submit a job
hp job submit examples/hello-world.pipeline.kts --name hello-world

# 5. Monitor job
hp job status <job-id>
hp job logs <job-id> -f

# 6. Check results
hp job list --status completed
```

### Multi-Environment Setup

```bash
# Setup development environment
hp login http://localhost:8080 -u admin -p admin123 --context dev

# Setup production environment  
hp login https://prod.hodei.io -u operator -p secretpass --context prod

# Switch between environments
hp config use-context dev
hp job submit test.kts

hp config use-context prod  
hp job submit deploy.kts
```

## Default User Credentials

When the orchestrator starts, it automatically creates these default users:

| Username | Password | Roles | Description |
|----------|----------|-------|-------------|
| `admin` | `admin123` | ADMIN | Full system access |
| `user` | `user123` | USER | Standard user access |
| `moderator` | `mod123` | MODERATOR | Elevated permissions |

## Troubleshooting

### Connection Issues
```bash
# Check if orchestrator is running
curl http://localhost:8080/v1/health

# Test with insecure connection
hp login http://localhost:8080 --insecure

# Check CLI configuration
hp config current-context
```

### Authentication Issues
```bash
# Check current user
hp whoami

# Re-login
hp logout
hp login http://localhost:8080
```

### Job Issues
```bash
# Check job logs for errors
hp job logs <job-id>

# Check worker status
hp worker list

# Check pool status
hp pool list
```

## Exit Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | General error |
| 2 | Authentication error |
| 3 | Connection error |
| 4 | Resource not found |
| 5 | Invalid arguments |

## Support

For more information:
- Documentation: [Hodei Pipelines Docs](docs/)
- Examples: [examples/](examples/)
- Issues: [GitHub Issues](https://github.com/rubentxu/hodei-pipelines/issues)