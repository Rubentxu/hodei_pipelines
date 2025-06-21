# Product Context: Hodei Pipelines

## ¿Por qué Hodei Pipelines?

En el ecosistema de JVM, herramientas como Jenkins (con Groovy) y Gradle (con Groovy/Kotlin) han demostrado el poder de usar un lenguaje de scripting para definir pipelines de CI/CD. Sin embargo, a menudo estas soluciones no están diseñadas desde cero pensando en Kotlin, lo que puede llevar a una experiencia de desarrollo que no es del todo idiomática.

Hodei Pipelines nace para llenar ese vacío, ofreciendo una solución de CI/CD donde Kotlin no es solo un lenguaje soportado, sino el lenguaje principal. Esto permite aprovechar al máximo las características de Kotlin, como el sistema de tipos estáticos, las corrutinas y un DSL limpio, para crear pipelines más seguros, mantenibles y fáciles de entender.

## ¿Cómo debería funcionar?

A alto nivel, un usuario definirá su pipeline en un archivo `*.pipeline.kts`. Este archivo será detectado por el sistema Hodei, que asignará un `worker` para ejecutarlo. El `worker` compilará y ejecutará el script, interpretando el DSL para realizar las acciones definidas (compilar código, ejecutar pruebas, construir imágenes de Docker, etc.).

La experiencia del usuario debe ser fluida, con retroalimentación clara sobre el progreso y los resultados de la ejecución del pipeline.
