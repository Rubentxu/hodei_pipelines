# Project Structure: Hodei Pipelines

```
hodei-pipelines/
├── core/
│   ├── domain/
│   └── application/
├── worker/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
│       ├── script/         # Lógica del DSL y ejecución de scripts
│       ├── worker/         # Implementación del worker
│       └── ...
├── build.gradle.kts
└── settings.gradle.kts
```
