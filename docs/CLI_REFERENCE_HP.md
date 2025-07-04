# Hodei Pipelines CLI Reference (`hp`)

The Hodei Pipelines CLI (`hp`) is a command-line client for interacting with the Hodei orchestrator. Similar to `kubectl` or `oc`, it provides a complete interface for managing distributed job execution, resource pools, workers, and templates.

> **Note**: This document covers the full roadmap of CLI features. Features marked with 🚧 are planned but not yet implemented.

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
hp pool create [options]
```

**Options:**
- `--name`: Name of the resource pool (required)
- `--type`: Pool type (docker, kubernetes) (default: docker)
- `--max-workers`: Maximum number of workers (default: 5)
- `--provider`: Provider (docker, local) (default: docker)
- `--dry-run`: Validate configuration without creating

**Examples:**
```bash
# Create Docker pool
hp pool create --name my-docker-pool --type docker --max-workers 5

# Create with dry-run validation
hp pool create --name test-pool --dry-run

# Create with custom provider
hp pool create --name k8s-pool --type kubernetes --provider local
```

### pool delete
Delete a resource pool.

```bash
hp pool delete <pool-id> [options]
```

**Arguments:**
- `pool-id`: ID of the resource pool to delete

**Options:**
- `--force, -f`: Force deletion without confirmation

**Examples:**
```bash
# Delete with confirmation
hp pool delete pool-12345

# Force delete without confirmation
hp pool delete pool-12345 --force
```

### pool status
Show detailed status of a resource pool.

```bash
hp pool status <pool-id>
```

**Examples:**
```bash
hp pool status pool-12345
```

### pool describe
Show comprehensive information about a resource pool (similar to kubectl describe).

```bash
hp pool describe <pool-id> [options]
```

**Arguments:**
- `pool-id`: ID of the resource pool

**Options:**
- `--output, -o`: Output format (text, json)

**Examples:**
```bash
# Detailed pool information
hp pool describe pool-12345

# JSON output
hp pool describe pool-12345 -o json
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
- `--pool`: Target resource pool ID
- `--priority`: Job priority (low, normal, high) (default: normal)
- `--timeout`: Job timeout in seconds
- `--dry-run`: Validate pipeline without submitting

**Examples:**
```bash
# Submit basic job
hp job submit my-pipeline.kts

# Submit with custom name and pool
hp job submit build.kts --name "build-v1.2.3" --pool pool-12345

# Submit with dry-run validation
hp job submit test.kts --dry-run

# Submit with priority and timeout
hp job submit deploy.kts --priority high --timeout 3600
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
Show logs for a job with real-time streaming support.

```bash
hp job logs <job-id> [options]
```

**Options:**
- `--follow, -f`: Follow log output (real-time streaming)
- `--tail, -n`: Number of lines to show from the end
- `--since`: Show logs since timestamp

**Examples:**
```bash
# Show job logs
hp job logs job-12345

# Follow logs in real-time
hp job logs job-12345 -f

# Show last 100 lines
hp job logs job-12345 --tail 100

# Show logs since specific time
hp job logs job-12345 --since 2024-01-01T10:00:00Z
```

### job cancel
Cancel a running job.

```bash
hp job cancel <job-id> [options]
```

**Arguments:**
- `job-id`: ID of the job to cancel

**Options:**
- `--reason`: Reason for cancellation
- `--force, -f`: Force cancellation without confirmation

**Examples:**
```bash
# Cancel with confirmation
hp job cancel job-12345

# Cancel with reason
hp job cancel job-12345 --reason "Build timeout"

# Force cancel without confirmation
hp job cancel job-12345 --force
```

### job describe
Show comprehensive information about a job (similar to kubectl describe).

```bash
hp job describe <job-id> [options]
```

**Arguments:**
- `job-id`: ID of the job

**Options:**
- `--output, -o`: Output format (text, json)

**Examples:**
```bash
# Detailed job information
hp job describe job-12345

# JSON output
hp job describe job-12345 -o json
```

### job exec 🚧
Execute a command in a job's execution context.

```bash
hp job exec <job-id> [options] -- <command>
```

**Arguments:**
- `job-id`: ID of the running job
- `command`: Command to execute

**Options:**
- `-i, --stdin`: Pass stdin to the container
- `-t, --tty`: Allocate a pseudo-TTY

**Examples:**
```bash
# Execute command in job context
hp job exec job-12345 -- ls -la

# Interactive command execution
hp job exec job-12345 -it -- /bin/bash
```

### job shell 🚧
Start an interactive shell in a job's execution context.

```bash
hp job shell <job-id> [options]
```

**Arguments:**
- `job-id`: ID of the running job

**Options:**
- `--shell`: Shell to use (default: /bin/bash)

**Examples:**
```bash
# Start interactive shell
hp job shell job-12345

# Use specific shell
hp job shell job-12345 --shell /bin/zsh
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

### worker describe
Show comprehensive information about a worker (similar to kubectl describe).

```bash
hp worker describe <worker-id> [options]
```

**Arguments:**
- `worker-id`: ID of the worker

**Options:**
- `--output, -o`: Output format (text, json)

**Examples:**
```bash
# Detailed worker information
hp worker describe worker-123

# JSON output
hp worker describe worker-123 -o json
```

### worker exec 🚧
Execute a command in a worker.

```bash
hp worker exec <worker-id> [options] -- <command>
```

**Arguments:**
- `worker-id`: ID of the worker
- `command`: Command to execute

**Options:**
- `-i, --stdin`: Pass stdin to the container
- `-t, --tty`: Allocate a pseudo-TTY

**Examples:**
```bash
# Execute command in worker
hp worker exec worker-123 -- ls -la

# Interactive command execution
hp worker exec worker-123 -it -- /bin/bash
```

### worker shell 🚧
Start an interactive shell in a worker.

```bash
hp worker shell <worker-id> [options]
```

**Arguments:**
- `worker-id`: ID of the worker

**Options:**
- `--shell`: Shell to use (default: /bin/bash)

**Examples:**
```bash
# Start interactive shell
hp worker shell worker-123

# Use specific shell
hp worker shell worker-123 --shell /bin/zsh
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
hp template create [options]
```

**Options:**
- `--name`: Template name (required)
- `--description`: Template description (required)
- `--type`: Template type (default: pipeline)
- `--file, -f`: Template definition file (JSON/YAML) (required)
- `--dry-run`: Validate template without creating

**Examples:**
```bash
# Create from file
hp template create --name my-template --description "My custom template" -f template.json

# Create with dry-run validation
hp template create --name test-template --description "Test template" -f template.json --dry-run

# Create different type
hp template create --name docker-template --description "Docker template" --type docker -f docker-template.json
```

### template show
Show detailed information about a template.

```bash
hp template show <template-id> [options]
```

**Arguments:**
- `template-id`: ID of the template

**Options:**
- `--output, -o`: Output format (text, json)

**Examples:**
```bash
# Show template details
hp template show template-12345

# JSON output
hp template show template-12345 -o json
```

### template describe
Show comprehensive information about a template (similar to kubectl describe).

```bash
hp template describe <template-id> [options]
```

**Arguments:**
- `template-id`: ID of the template

**Options:**
- `--output, -o`: Output format (text, json)

**Examples:**
```bash
# Detailed template information
hp template describe template-12345

# JSON output
hp template describe template-12345 -o json
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