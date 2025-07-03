plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    implementation(project(":pipeline-dsl:core"))
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Librerías para implementar los steps
    implementation("org.apache.commons:commons-lang3:3.13.0")
    implementation("org.apache.commons:commons-io:1.3.2") 
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.15.3")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("org.apache.ant:ant:1.10.14")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    implementation("org.jsoup:jsoup:1.16.2")
    
    // JSON Path y XPath
    implementation("com.jayway.jsonpath:json-path:2.8.0")
    implementation("net.sf.saxon:Saxon-HE:12.3")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "dev.rubentxu.hodei"
            artifactId = "pipeline-steps-library"
            version = project.version.toString()
            
            pom {
                name.set("Hodei Pipeline Steps Library")
                description.set("Complete library of pipeline steps compatible with Jenkins")
                url.set("https://github.com/rubentxu/hodei-pipelines")
            }
        }
    }
}