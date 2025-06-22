# Contexto Técnico: hodei-pipelines

## 1. Stack Tecnológico

- **Lenguaje**: [Kotlin](https://kotlinlang.org/)
- **Framework de Build**: [Gradle](https://gradle.org/)
- **Comunicación Remota**: [gRPC](https://grpc.io/) con [Protocol Buffers](https://developers.google.com/protocol-buffers) para la definición de servicios y mensajes.
- **Testing**: (A completar: Especificar librerías como JUnit, MockK, etc.)
- **Contenedores**: (A completar: Especificar si se usa Docker, etc.)

## 2. Herramientas de Desarrollo

- **Control de Versiones**: Git
- **CI/CD**: GitHub Actions (configurado en `.github/workflows/build.yml`)

## 3. Configuración del Entorno

Para construir y ejecutar el proyecto, es necesario tener instalado un JDK (versión recomendada a completar) y utilizar el Gradle Wrapper (`./gradlew`) proporcionado.

**Comandos clave:**

- `build`: `./gradle build`
- `test`: `./gradle test`
- `run`: (A completar: Especificar cómo ejecutar los componentes `backend` y `worker`)