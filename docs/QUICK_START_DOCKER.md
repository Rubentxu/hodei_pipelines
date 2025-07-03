# Quick Start Guide: Run Job with Docker in Hodei Pipelines

This guide shows you how to start the orchestrator and run a job using the new Hodei CLI (`hp`) and Docker on your local machine.

## Prerequisites

- Java 17+ installed
- Docker installed and running
- Gradle installed (don't use gradlew according to project configuration)

## Step 1: Build the Project

```bash
# From the project root directory
gradle clean build -x test
```

## Step 2: Start the Orchestrator

```bash
# Option 1: Using gradle
gradle :orchestrator:run

# Option 2: Using the generated JAR
java -jar orchestrator/build/libs/orchestrator-all.jar

# The orchestrator will start at http://localhost:8080
```

The orchestrator will automatically:
- **Bootstrap default users** (admin/admin123, user/user123, moderator/mod123)
- **Discover Docker environment** and register it as a resource pool
- **Create default worker templates** for different scenarios
- Expose REST API on port 8080
- Expose gRPC server on port 9090 (for worker communication)

Expected startup output:
```
🚀 Starting system bootstrap...
🔐 Initializing default users...
✅ Created user: admin with roles: [ADMIN]
✅ Created user: user with roles: [USER]  
✅ Created user: moderator with roles: [MODERATOR]

🔑 Default User Credentials (for CLI testing):
==================================================
👑 Admin:     admin / admin123
👤 User:      user / user123
🛡️ Moderator: moderator / mod123
==================================================
💡 Use these credentials with: hp login http://localhost:8080

🏗️ Docker environment registered as resource pool
📋 Default worker templates created: 7 templates
==================================================
🔗 CLI Usage: hp login http://localhost:8080
🔗 Health Check: curl http://localhost:8080/v1/health
```

## Step 3: Setup the Hodei CLI (`hp`)

```bash
# Build the CLI
gradle :hodei-pipelines-cli:build

# Add to PATH for convenience (optional)
export PATH=$PATH:$(pwd)/hodei-pipelines-cli/build/distributions/hodei-pipelines-cli/bin

# Verify installation
hp version
```

## Step 4: Login to Orchestrator

```bash
# Login with admin credentials
hp login http://localhost:8080 --username admin --password admin123

# Or login interactively
hp login http://localhost:8080

# Verify login
hp whoami
```

Expected output:
```
Username: admin
Email: admin@hodei.local
Roles: [ADMIN]
Context: default
Server: http://localhost:8080
```

## Step 5: Verify System Status

```bash
# Check orchestrator health
hp health

# Check system status
hp status

# List available resources
hp pool list
hp template list
hp worker list
```

Expected output:
```bash
$ hp pool list
NAME                   TYPE     WORKERS  STATUS
auto-discovered-docker docker   0/2      healthy

$ hp template list
NAME                      TYPE     IMAGE                    CPU    MEMORY
default-docker-worker     docker   hodei/worker:latest      100m   256Mi
performance-docker-worker docker   hodei/worker:latest      1000m  2Gi
docker-ci-pipeline-worker docker   hodei/worker-ci:latest   500m   1Gi
...
```

## Step 6: Submit Your First Job

Now you can submit a job using the pipeline DSL:

```bash
# Submit a job using the HP CLI
hp job submit examples/hello-world.pipeline.kts --name "my-first-job"

# Expected output:
# ✅ Job submitted successfully!
# 🆔 Job ID: job-abc123-def456
# 📊 Status: queued
# 
# 💡 Monitor with: hp job status job-abc123-def456
# 📋 View logs: hp job logs job-abc123-def456 -f
```

### Monitor Job Execution

```bash
# Check job status
hp job status job-abc123-def456

# Follow real-time logs
hp job logs job-abc123-def456 --follow

# List all jobs
hp job list

# List only running jobs
hp job list --status running
```

Expected log output:
```
🔍 Checking environment...
Greeting: Hello from Hodei Pipelines!
Build Number: 1.0.0
...
🎉 Pipeline completed successfully!
```

### Alternative: Using Pipeline DSL CLI Directly

You can also use the pipeline DSL CLI for local execution:

```bash
# Build the pipeline DSL CLI
gradle :pipeline-dsl:pipeline-cli:build

# Execute locally
./pipeline-dsl/pipeline-cli/build/install/pipeline-cli/bin/pipeline-dsl execute \
  examples/hello-world.pipeline.kts \
  --verbose

# Execute remotely through orchestrator
./pipeline-dsl/pipeline-cli/build/install/pipeline-cli/bin/pipeline-dsl execute \
  examples/hello-world.pipeline.kts \
  --orchestrator http://localhost:8080 \
  --pool auto-discovered-docker \
  --follow
```

## Step 7: Advanced Usage

### Create Custom Templates

```bash
# Create a custom template for Node.js projects
hp template create nodejs-worker \
  --type docker \
  --image node:18-alpine \
  --cpu 1 \
  --memory 2Gi

# Create from file
hp template create my-template --file template.yaml
```

### Manage Multiple Contexts

```bash
# Add production environment
hp login https://prod.hodei.io --username operator --password secret --context prod

# List contexts
hp config get-contexts

# Switch between environments
hp config use-context prod
hp job submit prod-deploy.kts

hp config use-context default
hp job submit test-build.kts
```

### Advanced Job Submission

```bash
# Submit with parameters
hp job submit deploy.kts \
  --parameters '{"env": "staging", "version": "1.2.3"}' \
  --wait \
  --timeout 30m

# Submit with parameters from file
hp job submit deploy.kts --parameters @params.json

# Submit to specific pool and template
hp job submit heavy-task.kts \
  --pool performance-pool \
  --template gpu-worker
```

## Monitoring and Debugging

### Using the HP CLI

```bash
# Check system health
hp health

# View system status
hp status

# List all resources
hp pool list
hp worker list
hp template list
hp job list

# Monitor specific job
hp job status job-abc123
hp job logs job-abc123 --follow --since 1h

# Cancel running job
hp job cancel job-abc123
```

### Using the REST API

```bash
# Health check
curl http://localhost:8080/v1/health

# List jobs
curl http://localhost:8080/v1/jobs

# Job details
curl http://localhost:8080/v1/jobs/{job-id}

# Job logs (with authentication)
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/v1/jobs/{job-id}/logs
```

## Troubleshooting

### CLI Connection Issues
```bash
# Check if orchestrator is running
hp health

# Test with different context
hp config get-contexts
hp config use-context <other-context>

# Re-login if authentication fails
hp logout
hp login http://localhost:8080
```

### Orchestrator Issues
```bash
# Check health endpoint
curl http://localhost:8080/v1/health

# Verify default users are created
# (check orchestrator startup logs for bootstrap information)

# Check if ports are available
netstat -tulpn | grep -E ':(8080|9090)'
```

### Docker Issues
```bash
# Verify Docker is running
docker version
docker ps

# Check Docker permissions (Linux)
sudo usermod -aG docker $USER
# (logout and login again)

# Check Docker connectivity
docker run hello-world
```

### Job Execution Issues
```bash
# Check job status and logs
hp job status <job-id>
hp job logs <job-id>

# List available resources
hp pool list
hp template list
hp worker list

# Check system status
hp status
```

## Complete Session Example

```bash
# Terminal 1: Start orchestrator
$ gradle :orchestrator:run
🚀 Starting system bootstrap...
✅ Created user: admin with roles: [ADMIN]
✅ Created user: user with roles: [USER]
✅ Created user: moderator with roles: [MODERATOR]
🏗️ Docker environment registered as resource pool
📋 Default worker templates created: 7 templates
🔗 CLI Usage: hp login http://localhost:8080

# Terminal 2: Use the HP CLI
$ hp login http://localhost:8080 -u admin -p admin123
✅ Login successful

$ hp status
🚀 Hodei Pipelines Status
==================================================
Server: http://localhost:8080
User: admin (ADMIN)
Health: ✅ Healthy

Resource Pools: 1
Templates: 7
Workers: 0
Jobs: 0

$ hp job submit examples/hello-world.pipeline.kts --name hello-world
✅ Job submitted successfully!
🆔 Job ID: job-abc123-def456

$ hp job logs job-abc123-def456 -f
🔍 Checking environment...
Greeting: Hello from Hodei Pipelines!
Build Number: 1.0.0
🎉 Pipeline completed successfully!
```

## Next Steps

1. **Customize pipelines**: Modify `hello-world.pipeline.kts` or create your own pipelines
2. **Configure templates**: Define templates specific to your needs
3. **Scale workers**: Increase the number of workers for parallel execution
4. **Integrate with CI/CD**: Use the REST API to integrate with existing systems

## References

- [Pipeline DSL Documentation](./PIPELINE_DSL.md)
- [REST API Reference](./API_REFERENCE.md)
- [Worker Templates Guide](./WORKER_TEMPLATES.md)
- [Architecture Overview](./ARCHITECTURE.md)