# Quick Start Guide: Hodei Pipelines with Docker

This comprehensive guide shows you how to start the orchestrator and use the enterprise-grade Hodei CLI (`hp`) to manage distributed job execution with Docker on your local machine.

## Prerequisites

### For Standard Installation
- **Java 17+** installed
- **Docker** installed and running
- **Gradle** installed (don't use gradlew according to project configuration)

### For Native Binary (Optional but Recommended)
- **GraalVM 21+** installed (for native compilation)
- Alternative: Download pre-built native binaries from releases

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

### Option A: Native Binary (Recommended) ⚡

For fastest startup and no JVM dependency:

```bash
# Build native binary (requires GraalVM)
gradle :hodei-pipelines-cli:nativeCompile

# Use the native binary directly
./hodei-pipelines-cli/build/native/nativeCompile/hp version

# Or create complete distribution
gradle :hodei-pipelines-cli:createNativeDistributions

# Install system-wide (optional)
sudo cp hodei-pipelines-cli/build/distributions/native/linux-x64/hp /usr/local/bin/
hp version
```

**Benefits of native binary:**
- ⚡ **Ultra-fast startup** (no JVM overhead)
- 🚀 **No Java required** (standalone executable)
- 📦 **Single file distribution** (58MB self-contained)
- 🔧 **All CLI features** (35+ commands included)

### Option B: JAR Distribution (Traditional)

If GraalVM is not available:

```bash
# Build the CLI
gradle :hodei-pipelines-cli:assemble

# Extract the distribution
cd hodei-pipelines-cli/build/distributions
tar -xf hodei-pipelines-cli.tar
cd hodei-pipelines-cli/bin

# Make executable and add to PATH (optional)
chmod +x hp
export PATH=$PATH:$(pwd)

# Verify installation
./hp version
```

Expected output:
```
🚀 Hodei Pipelines CLI
Version: 1.1.0-SNAPSHOT
Build: $(git rev-parse --short HEAD)

🖥️ Server: Not connected
💡 Use 'hp login <url>' to connect to an orchestrator
```

### GraalVM Installation (For Native Binary)

If you want to build native binaries yourself:

```bash
# Install GraalVM using SDKMAN (recommended)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce

# Verify GraalVM installation
java -version
native-image --version
```

Expected GraalVM output:
```
openjdk version "21.0.2" 2024-01-16
OpenJDK Runtime Environment GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)
```

## Step 4: Login to Orchestrator

```bash
# Login with admin credentials
hp login http://localhost:8080 --username admin --password admin123

# Or login interactively (prompts for credentials)
hp login http://localhost:8080

# Verify login
hp whoami
```

Expected output:
```
✅ Login successful!

Username: admin
Orchestrator URL: http://localhost:8080
Context: default
```

## Step 5: Verify System Status

```bash
# Check orchestrator health
hp health

# Check comprehensive system status
hp status

# List available resources
hp pool list
hp template list
hp worker list
```

Expected output:
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

## Step 6: Submit Your First Job

### Basic Job Submission

```bash
# Submit a job using a pipeline file
hp job submit examples/hello-world.pipeline.kts --name "my-first-job"

# Submit with additional options
hp job submit examples/hello-world.pipeline.kts \
  --name "my-first-job" \
  --priority high \
  --pool pool-123 \
  --timeout 300
```

Expected output:
```
📤 Submitting job from 'examples/hello-world.pipeline.kts'...

✅ Job submitted successfully!
   Job ID: job-abc123-def456
   Status: queued
   Queue Position: 1

💡 Track job progress with: hp job status job-abc123-def456
💡 View logs with: hp job logs job-abc123-def456
```

### Validate Before Submitting (Dry-run)

```bash
# Test your pipeline without actually submitting it
hp job submit examples/hello-world.pipeline.kts --dry-run
```

Expected output:
```
🔍 Dry-run mode: Validating pipeline...
  Name: hello-world
  Priority: normal
✅ Pipeline validation successful. Job would be submitted.
```

## Step 7: Monitor Job Execution

### Check Job Status

```bash
# Get detailed job information
hp job status job-abc123-def456

# Get comprehensive job details (kubectl-style)
hp job describe job-abc123-def456
```

Expected output:
```bash
$ hp job status job-abc123-def456
📊 Job Status
═════════════
ID:          job-abc123-def456
Name:        my-first-job
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

### Real-time Log Monitoring

```bash
# Follow real-time logs with color-coded output
hp job logs job-abc123-def456 --follow

# Show last 50 lines
hp job logs job-abc123-def456 --tail 50

# Show logs since a specific time
hp job logs job-abc123-def456 --since 2024-07-04T10:30:00Z
```

Expected log output:
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

### List and Filter Jobs

```bash
# List all jobs
hp job list

# List only running jobs
hp job list --status running

# List only failed jobs  
hp job list --status failed
```

## Step 8: Advanced Resource Management

### Pool Management

```bash
# Create a new resource pool
hp pool create --name my-docker-pool --type docker --max-workers 10

# Create with dry-run validation
hp pool create --name test-pool --type kubernetes --dry-run

# Get detailed pool information
hp pool describe pool-123

# Delete a pool (with confirmation)
hp pool delete pool-123

# Force delete without confirmation
hp pool delete pool-123 --force
```

### Worker Management

```bash
# List workers with filtering
hp worker list --pool pool-123 --status idle

# Get detailed worker information
hp worker describe worker-789

# Check worker status
hp worker status worker-789
```

### Template Management

```bash
# Create a custom template
hp template create \
  --name nodejs-template \
  --description "Node.js 18 development environment" \
  --type docker \
  --file nodejs-template.json

# Validate template before creating
hp template create \
  --name test-template \
  --description "Test template" \
  --file template.json \
  --dry-run

# Show template details
hp template describe template-123

# List templates by type
hp template list --type docker
```

## Step 9: Interactive Access (Shell Commands)

### Worker Shell Access

```bash
# Execute commands in a worker
hp worker exec worker-789 -- ls -la

# Start an interactive shell in a worker
hp worker shell worker-789

# Use a specific shell
hp worker shell worker-789 --shell /bin/zsh
```

### Job Context Access

```bash
# Execute commands in a running job's context
hp job exec job-abc123-def456 -- cat /logs/output.log

# Start an interactive shell in the job environment
hp job shell job-abc123-def456

# Check job processes
hp job exec job-abc123-def456 -- ps aux
```

> **Note**: Shell access commands are currently in Phase 1 implementation and require gRPC streaming support on the server side.

## Step 10: Job Lifecycle Management

### Cancel Running Jobs

```bash
# Cancel with confirmation prompt
hp job cancel job-abc123-def456

# Cancel with a reason
hp job cancel job-abc123-def456 --reason "Build timeout exceeded"

# Force cancel without confirmation
hp job cancel job-abc123-def456 --force
```

Expected output:
```
⚠️  Warning: This will cancel job 'job-abc123-def456' and stop all processing.
Are you sure? Type 'yes' to confirm:
yes

🛑 Cancelling job 'job-abc123-def456'...
✅ Job cancelled successfully!

💡 View final status with: hp job status job-abc123-def456
```

## Step 11: Multi-Environment Setup

### Configure Multiple Contexts

```bash
# Add production environment
hp login https://prod.hodei.io --username operator --password secret --context prod

# Add staging environment
hp login https://staging.hodei.io --username dev --password dev123 --context staging

# List all contexts
hp config get-contexts

# Switch between environments
hp config use-context prod
hp job submit prod-deploy.kts

hp config use-context staging
hp job submit staging-test.kts

hp config use-context default
hp job submit local-dev.kts
```

### Current Context Information

```bash
# Show current active context
hp config current-context

# Show current user info
hp whoami
```

## Monitoring and Debugging

### System Health Monitoring

```bash
# Comprehensive health check
hp health

# System overview with metrics
hp status

# Check specific resource health
hp pool list
hp worker list --status offline
hp job list --status failed
```

### Debugging Failed Jobs

```bash
# Get job details
hp job describe job-failed-123

# Check job logs for errors
hp job logs job-failed-123

# Check worker status
hp worker describe worker-assigned-to-job

# Check pool capacity
hp pool describe pool-123
```

### CLI Connection Issues

```bash
# Test connection
hp health

# Check current authentication
hp whoami

# Re-authenticate if needed
hp logout
hp login http://localhost:8080

# Switch to different context
hp config get-contexts
hp config use-context <other-context>
```

## Complete Workflow Example

```bash
# Terminal 1: Start orchestrator
$ gradle :orchestrator:run
🚀 Starting system bootstrap...
✅ Created user: admin with roles: [ADMIN]
🏗️ Docker environment registered as resource pool
📋 Default worker templates created: 7 templates

# Terminal 2: Use the HP CLI
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

## Complete Example: Running a Heavy Job with Monitoring

This section guides you step-by-step to execute a computationally intensive job that generates extensive traces, from setup to complete monitoring.

### Step 1: Create Custom Template (if needed)

```bash
# Check existing templates
hp template list

# If no suitable templates exist, create a custom one
cat > heavy-compute-template.json << 'EOF'
{
  "name": "heavy-compute-worker",
  "description": "Template for computationally intensive tasks",
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

# Create the template
hp template create \
  --name heavy-compute-worker \
  --description "Template for heavy computation" \
  --type docker \
  --file heavy-compute-template.json

# Verify creation
hp template describe heavy-compute-worker
```

### Step 2: Create Dedicated Resource Pool

```bash
# Create pool specific for heavy jobs
hp pool create \
  --name heavy-compute-pool \
  --type docker \
  --max-workers 3 \
  --provider docker

# Verify pool status
hp pool describe heavy-compute-pool
```

### Step 3: Create Computationally Intensive Pipeline

Create `heavy-pipeline.kts` file with tasks that generate extensive traces:

```kotlin
pipeline {
    metadata {
        name = "heavy-computation-pipeline"
        description = "Computationally intensive pipeline for demonstration"
        version = "1.0.0"
    }
    
    stage("Preparation") {
        script {
            println("🚀 Starting computationally intensive pipeline...")
            val startTime = System.currentTimeMillis()
            env["START_TIME"] = startTime.toString()
            
            println("📊 Environment configuration:")
            println("  - Available memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
            println("  - Processors: ${Runtime.getRuntime().availableProcessors()}")
            println("  - Start timestamp: $startTime")
        }
    }
    
    stage("Intensive Calculations") {
        parallel {
            stage("Massive Fibonacci") {
                script {
                    println("🔢 Calculating Fibonacci sequence up to 45...")
                    fun fibonacci(n: Int): Long {
                        if (n <= 1) return n.toLong()
                        return fibonacci(n - 1) + fibonacci(n - 2)
                    }
                    
                    for (i in 30..45) {
                        val result = fibonacci(i)
                        println("  Fibonacci($i) = $result")
                        Thread.sleep(500) // Generate more traces
                    }
                    println("✅ Fibonacci completed")
                }
            }
            
            stage("Data Processing Simulation") {
                script {
                    println("⚙️ Simulating heavy data processing...")
                    val data = mutableListOf<Double>()
                    
                    // Generate random data
                    repeat(1000000) { i ->
                        data.add(Math.random() * 1000)
                        if (i % 100000 == 0) {
                            println("  Generated ${i + 1} elements...")
                        }
                    }
                    
                    // Process data
                    println("📈 Processing statistical data...")
                    val sum = data.sum()
                    val avg = sum / data.size
                    val max = data.maxOrNull() ?: 0.0
                    val min = data.minOrNull() ?: 0.0
                    
                    println("  📊 Statistical results:")
                    println("    - Total elements: ${data.size}")
                    println("    - Sum: $sum")
                    println("    - Average: $avg")
                    println("    - Maximum: $max")
                    println("    - Minimum: $min")
                    
                    // Simulate file writing
                    println("💾 Simulating results writing...")
                    repeat(10) { i ->
                        println("  Writing result-$i.txt file")
                        Thread.sleep(200)
                    }
                    println("✅ Data processing completed")
                }
            }
        }
    }
    
    stage("Stress Testing") {
        script {
            println("🔥 Running system stress tests...")
            
            // CPU intensive simulation
            val threads = mutableListOf<Thread>()
            repeat(4) { threadIndex ->
                val thread = Thread {
                    println("  🧵 Thread $threadIndex started")
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 10000) { // 10 seconds
                        // CPU intensive operation
                        var result = 0L
                        for (i in 1..100000) {
                            result += i * i
                        }
                        if ((System.currentTimeMillis() - startTime) % 1000 < 50) {
                            println("    Thread $threadIndex - Progress: ${(System.currentTimeMillis() - startTime) / 1000}s")
                        }
                    }
                    println("  ✅ Thread $threadIndex completed")
                }
                threads.add(thread)
                thread.start()
            }
            
            // Wait for all threads to complete
            threads.forEach { it.join() }
            println("🎯 Stress tests completed")
        }
    }
    
    stage("Finalization") {
        script {
            val endTime = System.currentTimeMillis()
            val startTime = env["START_TIME"]?.toLong() ?: endTime
            val duration = endTime - startTime
            
            println("🏁 Pipeline completed successfully!")
            println("📈 Execution statistics:")
            println("  - Total time: ${duration / 1000}s")
            println("  - End timestamp: $endTime")
            println("  - Memory used: ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024} MB")
            
            // Generate final summary
            println("📋 Summary of executed tasks:")
            println("  ✅ Fibonacci calculations (30-45)")
            println("  ✅ Processing of 1M elements")
            println("  ✅ Multi-thread stress tests")
            println("  ✅ File I/O simulation")
            
            env["EXECUTION_SUMMARY"] = "SUCCESS - Duration: ${duration}ms"
        }
    }
}
```

### Step 4: Execute Job and Monitor in Real-Time

```bash
# Submit job with specific configuration
hp job submit heavy-pipeline.kts \
  --name "heavy-computation-demo" \
  --priority high \
  --pool heavy-compute-pool \
  --timeout 600

# The above command will return something like:
# ✅ Job submitted successfully!
# Job ID: job-heavy-abc123-def456
# Status: queued

# Capture Job ID for monitoring
JOB_ID="job-heavy-abc123-def456"  # Replace with actual ID

# Monitor status in real-time
echo "🔍 Monitoring job: $JOB_ID"
hp job status $JOB_ID

# Follow logs in real-time (separate terminal)
hp job logs $JOB_ID --follow
```

### Step 5: Advanced Monitoring (Separate Terminal)

```bash
# Terminal 2: Continuous job status monitoring
while true; do
    clear
    echo "📊 Job Status: $JOB_ID"
    echo "=================================="
    hp job status $JOB_ID
    echo ""
    echo "🏊 Pool Status:"
    hp pool describe heavy-compute-pool
    echo ""
    echo "👷 Active Workers:"
    hp worker list --pool heavy-compute-pool
    echo ""
    echo "🕐 $(date) - Updating in 5s..."
    sleep 5
done
```

### Step 6: Post-Execution Analysis

```bash
# Once job completes, get detailed information
hp job describe $JOB_ID

# Get complete logs for analysis
hp job logs $JOB_ID > heavy-job-logs.txt

# Check metrics of worker that executed the job
WORKER_ID=$(hp job describe $JOB_ID | grep "Worker:" | awk '{print $2}')
hp worker describe $WORKER_ID

# Clean up resources if necessary
hp job list --status completed
```

### Expected Monitoring Output

During execution you'll see traces like:

```
📄 Following job logs for: job-heavy-abc123-def456
Press Ctrl+C to stop...
─────────────────────────────────────────
[13:45:01] 🚀 Starting computationally intensive pipeline...
[13:45:01] 📊 Environment configuration:
[13:45:01]   - Available memory: 3584 MB
[13:45:01]   - Processors: 4
[13:45:02] 🔢 Calculating Fibonacci sequence up to 45...
[13:45:02] ⚙️ Simulating heavy data processing...
[13:45:03]   Fibonacci(30) = 832040
[13:45:03]   Generated 100001 elements...
[13:45:04]   Fibonacci(31) = 1346269
[13:45:05]   Generated 200001 elements...
[13:45:06]   Fibonacci(32) = 2178309
[13:45:07]   📈 Processing statistical data...
[13:45:08]   📊 Statistical results:
[13:45:08]     - Total elements: 1000000
[13:45:08]     - Sum: 499847293.45
[13:45:09] 🔥 Running system stress tests...
[13:45:09]   🧵 Thread 0 started
[13:45:09]   🧵 Thread 1 started
[13:45:10]     Thread 0 - Progress: 1s
[13:45:11]     Thread 1 - Progress: 2s
[13:45:19]   ✅ Thread 0 completed
[13:45:20] 🏁 Pipeline completed successfully!
[13:45:20] 📈 Execution statistics:
[13:45:20]   - Total time: 19s
[13:45:20]   ✅ Fibonacci calculations (30-45)
```

This example allows you to see:
- **Resource creation** (templates and pools)
- **Heavy job execution** with multiple parallel stages
- **Real-time monitoring** with detailed logs
- **Post-execution analysis** of metrics and results

## Advanced Use Cases

### Native Binary Distribution

For production environments or CI/CD systems:

```bash
# Build native binary for distribution
gradle :hodei-pipelines-cli:createNativeDistributions

# Package for deployment
tar -czf hodei-cli-linux-x64.tar.gz \
  -C hodei-pipelines-cli/build/distributions/native/linux-x64 \
  hp README.md

# Deploy to production server
scp hodei-cli-linux-x64.tar.gz user@server:/opt/
ssh user@server "cd /opt && tar -xzf hodei-cli-linux-x64.tar.gz && sudo mv hp /usr/local/bin/"

# Verify deployment
ssh user@server "hp version && hp --help"
```

**Benefits for Production:**
- ⚡ **Instant startup** - No JVM warmup time
- 🔒 **Security** - No JVM attack surface
- 📦 **Portability** - Single binary, no dependencies
- 💾 **Memory efficiency** - Lower memory footprint
- 🚀 **CI/CD friendly** - Fast execution in pipelines

### Custom Pipeline with Parameters

Create a parameterized pipeline file `deploy.pipeline.kts`:
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

Submit with parameters:
```bash
# Submit with environment variables
hp job submit deploy.pipeline.kts \
  --name "deploy-v1.2.3" \
  --priority high \
  --timeout 600
```

### Batch Job Management

```bash
# Submit multiple jobs
for i in {1..5}; do
  hp job submit test-job.kts --name "test-job-$i"
done

# Monitor all jobs
hp job list --status running

# Cancel all running jobs (if needed)
hp job list --status running | grep job- | awk '{print $2}' | xargs -I {} hp job cancel {} --force
```

### Resource Pool Scaling

```bash
# Create high-performance pool
hp pool create \
  --name performance-pool \
  --type docker \
  --max-workers 20 \
  --provider docker

# Submit jobs to specific pool
hp job submit heavy-computation.kts \
  --name "heavy-task" \
  --pool performance-pool \
  --priority high
```

## Troubleshooting

### Common Issues and Solutions

#### CLI Connection Problems
```bash
# Check orchestrator status
curl http://localhost:8080/v1/health

# Verify CLI installation
hp version

# Re-authenticate
hp logout && hp login http://localhost:8080
```

#### Job Execution Issues
```bash
# Check system resources
hp status

# Verify pool availability
hp pool list

# Check worker status
hp worker list

# Review job details
hp job describe <job-id>
hp job logs <job-id>
```

#### Docker Integration Issues
```bash
# Verify Docker is running
docker version
docker ps

# Check Docker permissions (Linux)
sudo usermod -aG docker $USER
# (logout and login again)

# Test Docker connectivity
docker run hello-world
```

### Getting Help

```bash
# CLI help
hp --help
hp job --help
hp pool create --help

# Check CLI version
hp version

# System status overview
hp status
```

## Next Steps

1. **Explore Advanced Features**: Try the describe commands, shell access, and dry-run modes
2. **Create Custom Pipelines**: Develop pipelines specific to your workflow
3. **Configure Multi-Environment**: Set up staging and production contexts
4. **Integrate with CI/CD**: Use the REST API for automated deployments
5. **Scale Your Setup**: Create multiple pools and templates for different workloads

## References

- [Complete CLI Reference](./CLI_REFERENCE_HP.md) - Full command documentation
- [CLI Roadmap](./CLI_ROADMAP.md) - Feature comparison and roadmap
- [Pipeline DSL Guide](./PIPELINE_DSL.md) - Pipeline development guide
- [Architecture Overview](./ARCHITECTURE.md) - System architecture details

---

**🎉 Congratulations!** You now have a fully functional Hodei Pipelines setup with enterprise-grade CLI capabilities. The `hp` CLI provides 35+ commands for complete pipeline orchestration management.