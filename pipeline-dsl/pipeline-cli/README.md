# Pipeline CLI

CLI para la ejecución y validación de pipelines DSL.

## Compilación

### JAR Ejecutable (Fat JAR)

Para crear un JAR ejecutable con todas las dependencias incluidas:

```bash
gradle shadowJar
```

El JAR se genera en `build/libs/pipeline-cli.jar` y puede ejecutarse con:

```bash
java -jar build/libs/pipeline-cli.jar --help
```

### Binario Nativo con GraalVM

Para crear un binario nativo standalone que puede ejecutarse en cualquier sistema:

#### Prerrequisitos

1. **Instalar GraalVM**: Descarga e instala GraalVM desde [https://graalvm.org/](https://graalvm.org/)
2. **Configurar JAVA_HOME**: Configura la variable de entorno para apuntar a GraalVM
3. **Instalar native-image**: Ejecuta `gu install native-image` para instalar la herramienta native-image

#### Compilación

```bash
# Compilar el binario nativo
gradle nativeCompile

# El binario se genera en build/native/nativeCompile/
./build/native/nativeCompile/pipeline-cli --help
```

#### Características del Binario Nativo

- **Standalone**: No requiere JVM instalada en el sistema de destino
- **Arranque rápido**: Tiempo de inicio significativamente menor que el JAR
- **Menor consumo de memoria**: Footprint reducido en comparación con la JVM
- **Multiplataforma**: Se puede compilar para diferentes sistemas operativos

#### Configuración de GraalVM

El proyecto está configurado con las siguientes optimizaciones para GraalVM:

- `--no-fallback`: Fuerza compilación nativa pura
- `--initialize-at-build-time=kotlin`: Inicializa clases de Kotlin en tiempo de compilación
- `--initialize-at-build-time=kotlinx.coroutines`: Inicializa coroutines en tiempo de compilación
- `-H:+ReportExceptionStackTraces`: Habilita stack traces completos
- `-H:+PrintClassInitialization`: Debug de inicialización de clases

## Uso

```bash
# Mostrar ayuda
./pipeline-cli --help

# Mostrar información del DSL
./pipeline-cli info

# Validar un pipeline
./pipeline-cli validate mi-pipeline.pipeline.kts

# Compilar un pipeline
./pipeline-cli compile mi-pipeline.pipeline.kts

# Ejecutar un pipeline
./pipeline-cli execute mi-pipeline.pipeline.kts
```

## Distribución

El binario nativo puede distribuirse como un ejecutable independiente sin requerir JVM en el sistema de destino, simplificando la instalación y despliegue en diferentes entornos.