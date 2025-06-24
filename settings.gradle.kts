rootProject.name = "hodei-pipelines"


pluginManagement {
    repositories {
        gradlePluginPortal()
        google() // If you have Android components
        mavenCentral()
    }
}

include(
    ":core:domain",
    ":core:application",
    ":core:infrastructure",
    ":backend:application",
    ":backend:infrastructure",
    ":worker:app",
    ":worker:domain",
    ":worker:application",
    ":worker:infrastructure",
    ":pipeline-dsl:core",
    ":pipeline-dsl:pipeline-cli",
    ":pipeline-steps-library"
)