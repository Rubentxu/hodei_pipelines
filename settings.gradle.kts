rootProject.name = "hodei-pipelines"


pluginManagement {
    repositories {
        gradlePluginPortal()
        google() // If you have Android components
        mavenCentral()
    }
}

include(
    ":orchestrator",
    ":shared:proto",
    ":worker:core",
    ":worker:infrastructure",
    ":pipeline-dsl:core",
    ":pipeline-dsl:pipeline-cli",
    ":pipeline-dsl:pipeline-steps-library"
)