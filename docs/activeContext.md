# Contexto Activo

## 1. Enfoque Actual

La tarea en curso es la **documentación inicial del proyecto `hodei-pipelines`**. El objetivo es establecer el "Banco de Registro" para guiar el desarrollo futuro, asegurar la calidad y facilitar la incorporación de nuevos colaboradores (o mi propia re-sincronización después de un reinicio).

## 2. Estado BDD Actual

Actualmente, el ciclo BDD se encuentra en una fase previa a la definición de escenarios. Estamos estableciendo el marco documental y de arquitectura sobre el cual se definirán los comportamientos y escenarios Gherkin.

```mermaid
stateDiagram-v2
    [*] --> Documentando_Proyecto
    Documentando_Proyecto --> Definiendo_Escenarios : Marco establecido
    Definiendo_Escenarios --> Implementando_Funcionalidad
    Implementando_Funcionalidad --> Verificando_Escenarios
    Verificando_Escenarios -- Pasan --> Refactorizando
    Verificando_Escenarios -- Fallan --> Implementando_Funcionalidad
    Refactorizando --> [*]

    state Documentando_Proyecto {
        direction LR
        description: Fase actual
    }
```

## 3. Próximos Pasos

1.  Completar la creación de los archivos base del Banco de Registro.
2.  Analizar el código fuente existente en los módulos `core`, `backend` y `worker` para entender la funcionalidad ya implementada.
3.  Comenzar a definir los Casos de Uso (`usecases.md`) y los escenarios BDD basados en el análisis del código y los objetivos del proyecto.
4.  Crear un `README.md` de alto nivel para el proyecto.