rootProject.name = "hodei-pipelines"

include(
    ":core:domain",
    ":core:application",
    ":core:infrastructure",
    ":backend:application",
    ":backend:infrastructure",
    ":worker:app",
    ":worker:domain",
    ":worker:application",
    ":worker:infrastructure"
)