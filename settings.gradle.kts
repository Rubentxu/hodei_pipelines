rootProject.name = "hodei-pipelines"

include(
    ":core:domain",
    ":core:application",
    ":core:infrastructure",
    ":backend:application",
    ":backend:infrastructure",
    ":worker:application",
    ":worker:infrastructure"
)