# Use Cases and BDD Scenarios

# Use Cases and BDD Scenarios

Este documento contendrá la definición de los Casos de Uso y los escenarios BDD en formato Gherkin para guiar el desarrollo.

## Feature: Basic Pipeline Definition

**As a** pipeline author
**I want to** define a simple pipeline structure using the Kotlin DSL
**So that** I can create a valid and executable pipeline model.

### Scenario: A user defines a simple pipeline with one stage and one step

```gherkin
Feature: Basic Pipeline Definition

  Scenario: A user defines a simple pipeline with one stage and one step
    Given a pipeline definition
    When the pipeline has a stage named "Build"
    And the "Build" stage has a step that prints "Hello World"
    Then a valid pipeline model should be created
    And the model should have one stage named "Build"
    And the "Build" stage should have one step
```

---

### Scenario: The post block is executed on success

```gherkin
Feature: Post-execution Actions

  Scenario: The post block is executed on stage success
    Given a pipeline with a stage containing a successful step and a post block
    When the user executes the pipeline
    Then the output should contain the output of the post block
    And the execution should be successful
```

---

### Scenario: The post block is executed on failure

```gherkin
Feature: Post-execution Actions

  Scenario: The post block is executed on stage failure
    Given a pipeline with a stage containing a failing step and a post block
    When the user executes the pipeline
    Then the output should contain the output of the post block
    And the execution should be unsuccessful
```

---

### Scenario: A pipeline with a failing step stops execution

```gherkin
Feature: Error Handling

  Scenario: A pipeline with a failing step stops execution when failFast is enabled
    Given a pipeline with a stage containing a failing step followed by a successful step
    When the user executes the pipeline
    Then the output should not contain the output of the successful step
    And the execution should be unsuccessful
```

---

### Scenario: A user executes a pipeline with a checkout step

```gherkin
Feature: Extensible Steps

  Scenario: A user executes a pipeline with a checkout step
    Given a pipeline definition with a `checkout` step for "my-repo"
    When the user executes the pipeline
    Then the output should contain "Checking out my-repo..."
    And the execution should be successful
```

---

## Feature: Command-Line Interface (CLI)

**As a** developer
**I want** to execute a pipeline definition from a file using the command line
**So that** I can run my CI/CD processes easily.

### Scenario: A user executes a pipeline script from the CLI

```gherkin
Feature: Command-Line Interface (CLI)

  Scenario: A user executes a pipeline script from the CLI
    Given a pipeline definition file named "my-pipeline.pipeline.kts" with a step that prints "Hello from CLI"
    When the user runs the command "hodei-cli run my-pipeline.pipeline.kts"
    Then the output should contain "Hello from CLI"
    And the execution should be successful
```

---

## Feature: Workspace Mounting in Docker Agent

**As a** user
**I want** my pipeline steps to access the project files
**So that** I can build, test, and interact with my source code.

### Scenario: A user executes a step that reads a file from the workspace

```gherkin
Feature: Workspace Mounting in Docker Agent

  Scenario: A user executes a step that reads a file from the workspace
    Given a file named "test_file.txt" with content "Hello from workspace" exists in the workspace
    And a pipeline defined with a Docker agent using the "alpine:latest" image
    And the pipeline has a stage with a step that runs "cat /hodei/workspace/test_file.txt"
    When the pipeline is executed
    Then the output should contain "Hello from workspace"
```

---


## Feature: Agent Definition

**As a** user
**I want to** specify the execution environment for my pipeline
**So that** I can run my steps inside a container with the correct tools.

### Scenario: A user defines a pipeline with a Docker agent

```gherkin
Feature: Agent Definition

  Scenario: A user defines a pipeline with a Docker agent
    Given a pipeline definition
    When the pipeline specifies a Docker agent with the image "ubuntu:latest"
    Then a valid pipeline model should be created
    And the model should have an agent configured
    And the agent should be a Docker agent with the image "ubuntu:latest"
```

---

## Feature: Pipeline Execution with Agent

**As a** user
**I want to** execute a pipeline within its specified agent environment
**So that** I can ensure the steps run with the correct dependencies and tools.

### Scenario: A user executes a pipeline with a Docker agent

```gherkin
Feature: Pipeline Execution with Agent

  Scenario: A user executes a pipeline with a Docker agent
    Given a pipeline defined with a Docker agent using the "alpine:latest" image
    And the pipeline has a "Verify Environment" stage with a step that runs "cat /etc/os-release"
    When the pipeline is executed
    Then the output should contain "Alpine Linux"
```