# Casos de Uso y Escenarios BDD

Este documento define los casos de uso clave del sistema `hodei-pipelines` y sus correspondientes escenarios de comportamiento (BDD) utilizando la sintaxis Gherkin. Estos escenarios sirven como especificación ejecutable y guían el desarrollo y las pruebas.

## 1. Caso de Uso: Creación y Ejecución de un Trabajo

- **ID**: UC-001
- **Nombre**: Crear y ejecutar un nuevo trabajo.
- **Actor**: Usuario del sistema (a través de un cliente API).
- **Descripción**: Un usuario envía la definición de un trabajo al sistema. El sistema lo pone en cola y, cuando hay un worker disponible, lo ejecuta y reporta el resultado.

### Escenario: Ejecución exitosa de un trabajo de tipo comando

```gherkin
Feature: Ejecución de un trabajo

  Scenario: Un usuario ejecuta un trabajo simple de tipo comando y este se completa con éxito
    Given un servidor Hodei-Pipelines está en funcionamiento
    And un worker está registrado y disponible
    When un usuario envía un nuevo trabajo con el comando "echo 'Hola Mundo'"
    Then el trabajo se pone en cola con el estado 'QUEUED'
    And al poco tiempo, el trabajo se asigna al worker y su estado cambia a 'RUNNING'
    And el worker ejecuta el comando
    And el sistema recibe la salida "Hola Mundo"
    And finalmente, el estado del trabajo cambia a 'COMPLETED'
```

### Escenario: Un trabajo falla durante la ejecución

```gherkin
Feature: Ejecución de un trabajo

  Scenario: Un trabajo falla porque el comando no existe
    Given un servidor Hodei-Pipelines está en funcionamiento
    And un worker está registrado y disponible
    When un usuario envía un nuevo trabajo con un comando inválido como "comando-inexistente"
    Then el trabajo se pone en cola con el estado 'QUEUED'
    And al poco tiempo, el trabajo se asigna al worker y su estado cambia a 'RUNNING'
    And el worker intenta ejecutar el comando y falla
    And el estado del trabajo cambia a 'FAILED'
    And el sistema registra un mensaje de error indicando que el comando no se encontró
```

## 2. Caso de Uso: Registro de un Worker

- **ID**: UC-002
- **Nombre**: Registrar un nuevo worker en el sistema.
- **Actor**: Aplicación Worker.
- **Descripción**: Un nuevo worker se inicia y se registra en el servidor central para formar parte del pool de ejecución.

### Escenario: Registro exitoso de un nuevo worker

```gherkin
Feature: Registro de Worker

  Scenario: Un nuevo worker se inicia y se registra con éxito en el servidor
    Given un servidor Hodei-Pipelines está en funcionamiento
    When un nuevo worker se inicia y envía una solicitud de registro al servidor
    Then el servidor acepta el registro
    And el worker aparece en la lista de workers disponibles con el estado 'IDLE'
```
