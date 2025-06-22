plugins {
    alias(libs.plugins.kotlin.jvm)
}

tasks.jar {
    archiveBaseName.set("backend-infrastructure")
}

dependencies {
    implementation(project(":core:infrastructure"))
    implementation(project(":core:application"))
    implementation(project(":core:domain"))
    
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    
    // Kubernetes client
    implementation(libs.kubernetes.client)
    implementation(libs.kubernetes.client.extended)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)

}


tasks.test {
    useJUnitPlatform()
}