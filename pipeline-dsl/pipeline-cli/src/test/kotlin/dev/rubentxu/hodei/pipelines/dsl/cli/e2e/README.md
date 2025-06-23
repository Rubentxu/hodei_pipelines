# Pipeline DSL CLI - End-to-End Tests

Comprehensive end-to-end test suite for the Pipeline DSL CLI, ensuring production-ready quality and reliability.

## 📋 Overview

This test suite validates the complete functionality of the Pipeline DSL CLI through realistic usage scenarios, covering all commands and edge cases.

## 🏗️ Test Structure

### Test Classes

| Test Class | Purpose | Coverage |
|------------|---------|----------|
| `BaseE2ETest` | Base class with common utilities | Test infrastructure |
| `ExecuteCommandE2ETest` | Tests for `execute` command | Pipeline execution scenarios |
| `CompileCommandE2ETest` | Tests for `compile` command | Pipeline compilation validation |
| `ValidateCommandE2ETest` | Tests for `validate` command | Syntax validation testing |
| `InfoCommandE2ETest` | Tests for `info` command | DSL information display |
| `FullWorkflowE2ETest` | Integration workflow tests | Real-world usage patterns |
| `PipelineDslCliE2ETestSuite` | Complete test suite | All tests coordination |
| `E2ETestRunner` | Custom test runner | Advanced reporting |

### Test Pipelines

Located in `src/test/resources/pipelines/`:

| Pipeline | Purpose |
|----------|---------|
| `simple-success.pipeline.kts` | Basic successful execution |
| `simple-failure.pipeline.kts` | Intentional failure testing |
| `parallel-stages.pipeline.kts` | Parallel execution validation |
| `security-test.pipeline.kts` | Sandbox security testing |
| `invalid-syntax.pipeline.kts` | Syntax error validation |

## 🚀 Running Tests

### Prerequisites

```bash
# Build the CLI JAR first
gradle :pipeline-dsl:cli:build
```

### All Tests

```bash
# Run complete E2E test suite
gradle :pipeline-dsl:cli:test --tests "*E2ETest*"

# Run test suite specifically
gradle :pipeline-dsl:cli:test --tests "*E2ETestSuite"
```

### Specific Categories

```bash
# Execute command tests
gradle :pipeline-dsl:cli:test --tests "ExecuteCommandE2ETest"

# Compile command tests
gradle :pipeline-dsl:cli:test --tests "CompileCommandE2ETest"

# Validate command tests  
gradle :pipeline-dsl:cli:test --tests "ValidateCommandE2ETest"

# Info command tests
gradle :pipeline-dsl:cli:test --tests "InfoCommandE2ETest"

# Full workflow tests
gradle :pipeline-dsl:cli:test --tests "FullWorkflowE2ETest"
```

### Individual Tests

```bash
# Specific test method
gradle :pipeline-dsl:cli:test --tests "ExecuteCommandE2ETest.execute simple success pipeline should succeed"
```

## 📊 Test Coverage

### Execute Command (10 tests)
- ✅ Successful pipeline execution
- ✅ Failed pipeline with error handling
- ✅ Parallel stages execution
- ✅ Security sandbox constraints
- ✅ Custom job-id and worker-id
- ✅ Non-existent pipeline handling
- ✅ Syntax error handling
- ✅ Verbose vs minimal output
- ✅ Timeout handling
- ✅ Execution consistency

### Compile Command (9 tests)
- ✅ Simple pipeline compilation
- ✅ Parallel stages compilation
- ✅ Verbose detailed analysis
- ✅ Invalid syntax error handling
- ✅ Non-existent file handling
- ✅ Empty pipeline handling
- ✅ Large pipeline performance
- ✅ Complex syntax compilation
- ✅ Multiple pipeline consistency

### Validate Command (10 tests)
- ✅ Successful validation
- ✅ Invalid syntax detection
- ✅ Non-existent file handling
- ✅ Empty pipeline validation
- ✅ Semantic vs syntactic errors
- ✅ Complex syntax validation
- ✅ Multiple pipeline handling
- ✅ Performance vs compile comparison
- ✅ Large pipeline performance

### Info Command (11 tests)
- ✅ DSL information display
- ✅ Supported step types listing
- ✅ Available imports display
- ✅ Features showcase
- ✅ Quick execution
- ✅ No file requirements
- ✅ Version information
- ✅ Proper formatting
- ✅ Complete capabilities
- ✅ Consistency across runs
- ✅ Comprehensive help

### Full Workflow (5 tests)
- ✅ Complete development workflow
- ✅ Error handling workflow
- ✅ Performance workflow
- ✅ Parallel workflow demonstration
- ✅ Info command integration

**Total: 45 comprehensive E2E tests**

## 🎯 Test Scenarios

### Basic Functionality
- Command execution and output
- Error handling and exit codes
- File processing and validation
- Option parsing and defaults

### Edge Cases
- Non-existent files
- Invalid syntax
- Empty pipelines
- Large pipelines
- Permission issues

### Performance
- Execution time benchmarks
- Memory usage validation
- Timeout handling
- Scalability testing

### Real-World Usage
- Complete development workflows
- CI/CD pipeline scenarios
- Error recovery patterns
- Integration between commands

### Security
- Sandbox constraint validation
- File access restrictions
- Command execution limits
- Network access controls

## 📈 Performance Benchmarks

| Operation | Expected Time | Timeout |
|-----------|---------------|---------|
| Validation | < 3 seconds | 30 seconds |
| Compilation | < 10 seconds | 30 seconds |
| Simple Execution | < 10 seconds | 60 seconds |
| Complex Execution | < 60 seconds | 120 seconds |
| Info Command | < 2 seconds | 10 seconds |

## 🔧 Troubleshooting

### Common Issues

#### "CLI JAR not found"
```bash
# Solution: Build the CLI first
gradle :pipeline-dsl:cli:build
```

#### "Command timed out"
```bash
# Check system performance, increase timeout if needed
# Review test for potential infinite loops
```

#### "Permission denied"
```bash
# Ensure temp directory write permissions
# Check file system access rights
```

#### "Tests fail on Windows"
```bash
# Some parallel tests are disabled on Windows
# Check OS-specific test conditions
```

### Debug Steps

1. **Verify CLI Build**
   ```bash
   ls -la pipeline-dsl/cli/build/libs/
   java -jar pipeline-dsl/cli/build/libs/cli.jar info
   ```

2. **Test CLI Manually**
   ```bash
   java -jar cli.jar execute simple-success.pipeline.kts
   java -jar cli.jar compile simple-success.pipeline.kts
   java -jar cli.jar validate simple-success.pipeline.kts
   ```

3. **Check Test Environment**
   ```bash
   # Verify test resources
   ls -la src/test/resources/pipelines/
   
   # Check temp directory access
   echo $TMPDIR
   ```

4. **Run Individual Tests**
   ```bash
   # Isolate failing tests
   gradle :pipeline-dsl:cli:test --tests "ExecuteCommandE2ETest.execute simple success pipeline should succeed" --info
   ```

### Test Configuration

#### Environment Variables
- `TMPDIR`: Temporary directory for test artifacts
- `JAVA_HOME`: Java installation path
- `GRADLE_OPTS`: Gradle JVM options

#### System Requirements
- Java 17+
- Shell access (bash/cmd)
- File system write permissions
- Network access (some tests)

## 📝 Adding New Tests

### Test Class Template

```kotlin
class NewCommandE2ETest : BaseE2ETest() {
    
    @Test
    fun `test description should describe expected behavior`() {
        // Arrange
        val pipeline = createTempPipeline("test.pipeline.kts", "content")
        
        // Act
        val result = runCli("command", pipeline.absolutePath)
        
        // Assert
        result.assertSuccess()
        result.assertContains("expected output")
    }
}
```

### Pipeline Template

```kotlin
// Test pipeline description
pipeline("Test Pipeline") {
    description("Purpose of this test pipeline")
    
    environment {
        "TEST_VAR" to "value"
    }
    
    stages {
        stage("Test Stage") {
            steps {
                echo("Test step")
                sh("test command")
            }
        }
    }
    
    post {
        always {
            echo("Cleanup")
        }
    }
}
```

### Test Naming Conventions

- Test methods: `test description should describe expected behavior`
- Test files: `feature-test.pipeline.kts`
- Test classes: `FeatureE2ETest`
- Assertions: Descriptive failure messages

## 🏆 Quality Standards

### Test Quality Criteria
- ✅ Clear test descriptions
- ✅ Comprehensive assertions
- ✅ Proper error handling
- ✅ Performance considerations
- ✅ Cross-platform compatibility
- ✅ Realistic scenarios
- ✅ Edge case coverage

### Maintenance Guidelines
- Keep tests independent and isolated
- Use descriptive test names and assertions
- Maintain test pipeline resources
- Update tests when CLI changes
- Monitor performance benchmarks
- Review and refactor regularly

## 📊 Continuous Integration

### CI/CD Integration

```yaml
# Example GitHub Actions workflow
name: E2E Tests
on: [push, pull_request]

jobs:
  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build CLI
        run: gradle :pipeline-dsl:cli:build
      - name: Run E2E Tests
        run: gradle :pipeline-dsl:cli:test --tests "*E2ETest*"
      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-reports
          path: pipeline-dsl/cli/build/reports/tests/
```

### Reporting Integration

The `E2ETestRunner` provides detailed metrics and can be integrated with CI/CD systems for advanced reporting and analysis.

---

**🎯 Goal**: Ensure Pipeline DSL CLI is production-ready through comprehensive end-to-end testing that validates real-world usage scenarios and edge cases.