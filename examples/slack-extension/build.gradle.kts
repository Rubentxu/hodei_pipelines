plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    implementation(project(":pipeline-dsl:core"))
    implementation("com.slack.api:slack-api-client:1.29.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "com.example"
            artifactId = "slack-step-extension"
            version = "1.0.0"
            
            pom {
                name.set("Slack Step Extension")
                description.set("Slack integration for Hodei Pipelines")
                url.set("https://github.com/example/slack-step-extension")
            }
        }
    }
}