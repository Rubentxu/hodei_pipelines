rootProject.name = "hodei-pipelines"

include(
    ":core:domain",
    ":core:application", 
    ":backend:application",
    ":backend:infrastructure",
    ":worker:application",
    ":worker:infrastructure"
)