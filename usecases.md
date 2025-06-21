# Use Cases: Hodei Pipelines

## Caso de Uso: Ejecutar un Pipeline Básico

**Actor:** Desarrollador

**Descripción:** Un desarrollador quiere ejecutar un pipeline simple que imprime un mensaje en la consola.

**Escenario Gherkin:**

```gherkin
Feature: Ejecución de Pipeline

  Scenario: Ejecutar un script que imprime un mensaje
    Given un pipeline definido con el siguiente script:
      """
      println("Hola, Hodei!")
      """
    When el pipeline es ejecutado
    Then la salida debe contener "Hola, Hodei!"
```

## Caso de Uso: Ejecutar un Pipeline con Comandos de Shell

**Actor:** Desarrollador

**Descripción:** Un desarrollador quiere ejecutar un pipeline que corre un comando de shell.

**Escenario Gherkin:**

```gherkin
Feature: Ejecución de Pasos de Shell

  Scenario: Ejecutar un comando 'echo'
    Given un pipeline definido con el siguiente script:
      """
      sh("echo 'Comando ejecutado desde el pipeline'")
      """
    When el pipeline es ejecutado
    Then la salida debe contener "Comando ejecutado desde el pipeline"
```
