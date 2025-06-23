plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":pipeline-dsl:core"))
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("dev.rubentxu.hodei.pipelines.dsl.cli.MainKt")
}
