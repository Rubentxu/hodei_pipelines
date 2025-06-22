import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    `java-library`
}

allprojects {
    repositories {
        mavenCentral()
    }

    // Configuración de la versión de Java para todos los proyectos
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_21.toString()
        targetCompatibility = JavaVersion.VERSION_21.toString()
    }

    tasks.withType<Test> {
        useJUnitPlatform() // Para que Kotest funcione correctamente
        testLogging {
            events("passed", "skipped", "failed") // Muestra eventos de test en la consola
            exceptionFormat =
                org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL // Muestra stack traces completos
            showStandardStreams = true // Muestra stdout/stderr de los tests
        }
    }



}

tasks.register("allTests") {
    description = "Ejecuta todos los tests de los módulos hijos"
    group = "verification"

    // Dependencia dinámica en todas las tareas de test de submódulos
    dependsOn(subprojects.map { it.tasks.withType<Test>() })

    // Asegura que esta tarea se ejecute después de que se configuren todas las tareas de test en los submódulos
    subprojects.forEach { subproject ->
        mustRunAfter(subproject.tasks.withType<Test>())
    }
}

