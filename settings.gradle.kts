rootProject.name = "hodei-pipelines"

include(
    ":backend:app",
    ":backend:application",
    ":backend:domain",
    ":backend:infrastructure",
    ":worker:app",
    ":worker:application",
    ":worker:domain",
    ":worker:infrastructure",
    ":core:app",
    ":core:application",
    ":core:domain",
    ":core:infrastructure"
)