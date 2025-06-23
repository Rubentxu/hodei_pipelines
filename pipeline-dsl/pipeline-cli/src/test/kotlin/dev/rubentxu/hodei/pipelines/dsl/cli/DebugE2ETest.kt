package dev.rubentxu.hodei.pipelines.dsl.cli

import dev.rubentxu.hodei.pipelines.dsl.cli.e2e.BaseE2ETest
import org.junit.jupiter.api.Test
import java.io.File

class DebugE2ETest : BaseE2ETest() {
    
    @Test
    fun `debug CLI JAR location`() {
        println("Current working directory: ${System.getProperty("user.dir")}")
        println("Temp directory: $tempDir")
        
        try {
            println("Looking for CLI JAR...")
            val jarLocation = findCliJar()
            println("CLI JAR found at: ${jarLocation.absolutePath}")
            println("JAR exists: ${jarLocation.exists()}")
            println("JAR readable: ${jarLocation.canRead()}")
        } catch (e: Exception) {
            println("Error finding CLI JAR: ${e.message}")
            e.printStackTrace()
        }
        
        // Buscar manualmente
        val possibleLocations = listOf(
            "build/libs/cli.jar",
            "../cli/build/libs/cli.jar",
            "pipeline-dsl/cli/build/libs/cli.jar",
            "/home/rubentxu/Proyectos/Kotlin/hodei-pipelines/pipeline-dsl/cli/build/libs/cli.jar"
        )
        
        possibleLocations.forEach { location ->
            val file = File(location)
            println("Checking $location: exists=${file.exists()}")
        }
    }
    
    private fun findCliJar(): File {
        // Copia de la lógica de BaseE2ETest
        val buildDir = File("build/libs")
        if (buildDir.exists()) {
            val jarFiles = buildDir.listFiles { file -> 
                file.name.endsWith(".jar") && !file.name.contains("plain")
            }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                return jarFiles.first()
            }
        }
        
        val parentBuildDir = File("../cli/build/libs")
        if (parentBuildDir.exists()) {
            val jarFiles = parentBuildDir.listFiles { file -> 
                file.name.endsWith(".jar") && !file.name.contains("plain")
            }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                return jarFiles.first()
            }
        }
        
        throw IllegalStateException("CLI JAR not found")
    }
}