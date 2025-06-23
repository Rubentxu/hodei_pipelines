# Product Context: A Superior Pipeline DSL

## Por qué

Las soluciones existentes como el DSL de Jenkins (basado en Groovy) o configuraciones en YAML carecen de la seguridad de tipos y el soporte de IDE que los lenguajes modernos como Kotlin pueden ofrecer. Esto a menudo conduce a errores en tiempo de ejecución, una curva de aprendizaje más pronunciada y una experiencia de desarrollador deficiente.

Este proyecto nace para resolver esos problemas, creando un DSL para la definición de pipelines que sea:

- **Seguro en Tipos**: La mayoría de los errores se detectan en tiempo de compilación, no durante la ejecución del pipeline.
- **Expresivo y Legible**: La sintaxis debe ser clara, concisa y seguir los modismos de Kotlin.
- **Potente**: Aprovechar todo el poder de un lenguaje de programación completo para definir la lógica del pipeline.
- **Extensible**: Permitir que terceros añadan nuevas funcionalidades (pasos, agentes) de forma segura y desacoplada.

## Cómo

El DSL se construirá sobre una base sólida de principios de diseño de software y características de Kotlin. Se enfocará en proporcionar una estructura declarativa y jerárquica para definir pipelines, inspirada en los conceptos probados de `pipeline`, `stage`, `step`, pero mejorada con el sistema de tipos de Kotlin. La meta es ofrecer una experiencia de "Pipeline-as-Code" de primera clase, con autocompletado, refactorización segura y depuración estándar.