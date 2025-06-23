package dev.rubentxu.hodei.pipelines.dsl.cli

import dev.rubentxu.hodei.pipelines.dsl.cli.e2e.BaseE2ETest
import org.junit.jupiter.api.Test

class DebugRunCliTest : BaseE2ETest() {
    
    @Test
    fun `debug runCli method`() {
        println("Testing runCli method...")
        
        try {
            // Probar comando más básico primero
            println("Trying to run CLI with help command...")
            val result = runCli("--help", timeoutSeconds = 10)
            
            println("Exit code: ${result.exitCode}")
            println("Stdout length: ${result.stdout.length}")
            println("Stderr length: ${result.stderr.length}")
            println("Command: ${result.command}")
            
            if (result.stdout.isNotEmpty()) {
                println("Stdout preview: ${result.stdout.take(200)}")
            }
            
            if (result.stderr.isNotEmpty()) {
                println("Stderr preview: ${result.stderr.take(200)}")
            }
            
        } catch (e: Exception) {
            println("Error running CLI: ${e.message}")
            e.printStackTrace()
        }
    }
    
    @Test
    fun `test info command specifically`() {
        println("Testing info command...")
        
        try {
            val result = runCli("info", timeoutSeconds = 10)
            
            println("Info command result:")
            println("Exit code: ${result.exitCode}")
            println("Success: ${result.isSuccess}")
            println("Stdout: '${result.stdout}'")
            println("Stderr: '${result.stderr}'")
            
            // Verificar si hay problemas con dependencias
            if (result.stderr.contains("ClassNotFoundException") || 
                result.stderr.contains("NoClassDefFoundError")) {
                println("❌ Dependency/classpath issue detected")
            }
            
            if (result.stderr.contains("FileNotFoundException") || 
                result.stderr.contains("No such file")) {
                println("❌ File not found issue detected")
            }
            
        } catch (e: Exception) {
            println("❌ Exception running info command: ${e.message}")
            e.printStackTrace()
        }
    }
}