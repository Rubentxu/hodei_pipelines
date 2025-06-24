package dev.rubentxu.hodei.pipelines.steps.e2e

import dev.rubentxu.hodei.pipelines.dsl.builders.*
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineExecutor
import dev.rubentxu.hodei.pipelines.steps.dsl.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Tests E2E de rendimiento y concurrencia para la librería de steps.
 * Verifica que el sistema puede manejar cargas pesadas y operaciones concurrentes.
 */
class PerformanceE2ETest : E2ETestBase() {
    
    @Test
    fun `concurrent step execution should be efficient`() = runBlocking {
        // Given - Pipeline con múltiples operaciones concurrentes
        val pipeline = pipeline("concurrent-performance-test") {
            description("Testing concurrent step execution performance")
            
            stages {
                stage("Concurrent File Operations") {
                    steps {
                        echo("🚀 Starting concurrent file operations test...")
                        
                        parallel(failFast = false) {
                            "JSON Processing" {
                                repeat(10) { i ->
                                    val data = mapOf(
                                        "id" to i,
                                        "name" to "item_$i",
                                        "timestamp" to System.currentTimeMillis(),
                                        "data" to (1..100).map { "value_$it" }
                                    )
                                    writeJSON("json/item_$i.json", data)
                                    val readData = readJSON("json/item_$i.json")
                                    echo("Processed JSON item $i")
                                }
                            }
                            
                            "YAML Processing" {
                                repeat(10) { i ->
                                    val data = mapOf(
                                        "apiVersion" to "v1",
                                        "kind" to "ConfigMap",
                                        "metadata" to mapOf("name" to "config-$i"),
                                        "data" to mapOf(
                                            "config.yaml" to "key: value_$i",
                                            "port" to (8000 + i).toString()
                                        )
                                    )
                                    writeYaml("yaml/config_$i.yaml", data)
                                    val readData = readYaml("yaml/config_$i.yaml")
                                    echo("Processed YAML config $i")
                                }
                            }
                            
                            "Properties Processing" {
                                repeat(10) { i ->
                                    val props = mapOf(
                                        "app.name" to "application_$i",
                                        "app.version" to "1.0.$i",
                                        "server.port" to (9000 + i).toString(),
                                        "database.url" to "jdbc:postgresql://localhost/db_$i"
                                    )
                                    writeProperties("props/app_$i.properties", props)
                                    val readProps = readProperties("props/app_$i.properties")
                                    echo("Processed properties file $i")
                                }
                            }
                            
                            "Hash Calculation" {
                                repeat(10) { i ->
                                    val content = "Test content for file $i - " + "x".repeat(1000)
                                    writeFile("data/file_$i.txt", content)
                                    val sha256Hash = sha256("data/file_$i.txt")
                                    val md5Hash = md5("data/file_$i.txt")
                                    echo("Calculated hashes for file $i")
                                }
                            }
                        }
                        
                        echo("✅ Concurrent file operations completed")
                    }
                }
                
                stage("Bulk Archive Operations") {
                    steps {
                        echo("📦 Starting bulk archive operations...")
                        
                        val startTime = System.currentTimeMillis()
                        
                        // Crear múltiples archivos ZIP
                        parallel(failFast = false) {
                            "ZIP Archives" {
                                repeat(5) { i ->
                                    zip("archives/data_$i.zip", "json/**/*")
                                    echo("Created ZIP archive $i")
                                }
                            }
                            
                            "TAR Archives" {
                                repeat(5) { i ->
                                    tar("archives/backup_$i.tar.gz", "yaml/**/*", compress = true)
                                    echo("Created TAR archive $i")
                                }
                            }
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        echo("Archive operations took ${duration}ms")
                        
                        // Verificar archivos creados
                        val zipFiles = findFiles("archives/*.zip")
                        val tarFiles = findFiles("archives/*.tar.gz")
                        
                        echo("Created ${zipFiles.size} ZIP files and ${tarFiles.size} TAR files")
                    }
                }
                
                stage("Stress Test File Operations") {
                    steps {
                        echo("💪 Running stress test with many small operations...")
                        
                        val operationCount = 100
                        val startTime = System.currentTimeMillis()
                        
                        repeat(operationCount) { i ->
                            // Crear archivo pequeño
                            writeFile("stress/file_$i.txt", "Content $i")
                            
                            // Verificar que existe
                            val exists = fileExists("stress/file_$i.txt")
                            
                            // Leer contenido
                            val content = readFile("stress/file_$i.txt")
                            
                            // Calcular hash
                            val hash = sha1("stress/file_$i.txt")
                            
                            if (i % 20 == 0) {
                                echo("Processed $i/$operationCount files...")
                            }
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        val opsPerSecond = (operationCount * 1000.0 / duration).toInt()
                        
                        echo("Completed $operationCount operations in ${duration}ms")
                        echo("Performance: $opsPerSecond operations/second")
                        
                        // Crear reporte de rendimiento
                        writeJSON("performance-report.json", mapOf(
                            "totalOperations" to operationCount,
                            "durationMs" to duration,
                            "operationsPerSecond" to opsPerSecond,
                            "testType" to "stress_test"
                        ))
                    }
                }
            }
            
            post {
                always {
                    echo("📊 Generating performance summary...")
                    
                    val allFiles = findFiles("**/*")
                    val jsonFiles = findFiles("**/*.json")
                    val yamlFiles = findFiles("**/*.yaml")
                    val propsFiles = findFiles("**/*.properties")
                    val archives = findFiles("**/*.{zip,tar.gz}")
                    
                    writeJSON("performance-summary.json", mapOf(
                        "totalFiles" to allFiles.size,
                        "jsonFiles" to jsonFiles.size,
                        "yamlFiles" to yamlFiles.size,
                        "propertiesFiles" to propsFiles.size,
                        "archives" to archives.size,
                        "testCompleted" to System.currentTimeMillis()
                    ))
                }
            }
        }
        
        // When - Ejecutar y medir tiempo total
        val totalTime = measureTimeMillis {
            val executor = PipelineExecutor(
                stepExecutorRegistry = stepExecutorRegistry,
                extensionRegistry = extensionRegistry
            )
            
            executor.execute(pipeline, pipelineContext)
            waitForCompletion(5000) // Esperar más tiempo para operaciones pesadas
        }
        
        // Then - Verificar rendimiento y resultados
        println("Total pipeline execution time: ${totalTime}ms")
        
        assertOutputContains("🚀 Starting concurrent file operations test...")
        assertOutputContains("✅ Concurrent file operations completed")
        assertOutputContains("📦 Starting bulk archive operations...")
        assertOutputContains("💪 Running stress test with many small operations...")
        assertOutputContains("📊 Generating performance summary...")
        
        // Verificar archivos de rendimiento
        assertFileExists("performance-report.json")
        assertFileExists("performance-summary.json")
        
        // Verificar que se crearon archivos en cantidad esperada
        val allFiles = workingDirectory.walkTopDown()
            .filter { it.isFile }
            .count()
        
        assertTrue(allFiles > 100, "Should have created many files, but found: $allFiles")
        assertTrue(totalTime < 30000, "Pipeline should complete in reasonable time, took: ${totalTime}ms")
        
        assertNoErrors()
    }
    
    @Test
    fun `memory intensive operations should not cause issues`() = runBlocking {
        // Given - Pipeline con operaciones que consumen memoria
        val pipeline = pipeline("memory-intensive-test") {
            description("Testing memory intensive operations")
            
            stages {
                stage("Large Data Processing") {
                    steps {
                        echo("🧠 Starting memory intensive operations...")
                        
                        // Crear archivos grandes con datos JSON
                        repeat(5) { fileIndex ->
                            val largeData = mapOf(
                                "fileIndex" to fileIndex,
                                "timestamp" to System.currentTimeMillis(),
                                "data" to (1..1000).map { itemIndex ->
                                    mapOf(
                                        "id" to itemIndex,
                                        "name" to "item_${fileIndex}_${itemIndex}",
                                        "description" to "This is a description for item $itemIndex in file $fileIndex. ".repeat(10),
                                        "metadata" to mapOf(
                                            "created" to System.currentTimeMillis(),
                                            "tags" to (1..20).map { "tag_$it" },
                                            "properties" to (1..50).associate { "prop_$it" to "value_$it" }
                                        )
                                    )
                                }
                            )
                            
                            writeJSON("large/data_$fileIndex.json", largeData, pretty = true)
                            echo("Created large JSON file $fileIndex")
                        }
                        
                        echo("📄 Large JSON files created")
                    }
                }
                
                stage("Parallel Processing") {
                    steps {
                        echo("⚡ Processing large files in parallel...")
                        
                        parallel(failFast = false) {
                            "Process File 0" {
                                val data = readJSON("large/data_0.json")
                                val hash = sha256("large/data_0.json")
                                writeFile("processed/summary_0.txt", "File 0: hash=$hash")
                            }
                            
                            "Process File 1" {
                                val data = readJSON("large/data_1.json")
                                val hash = sha256("large/data_1.json")
                                writeFile("processed/summary_1.txt", "File 1: hash=$hash")
                            }
                            
                            "Process File 2" {
                                val data = readJSON("large/data_2.json")
                                val hash = sha256("large/data_2.json")
                                writeFile("processed/summary_2.txt", "File 2: hash=$hash")
                            }
                            
                            "Archive Files" {
                                zip("large-data-archive.zip", "large/**/*")
                                val archiveHash = sha256("large-data-archive.zip")
                                echo("Archive hash: $archiveHash")
                            }
                        }
                        
                        echo("✅ Parallel processing completed")
                    }
                }
                
                stage("Memory Cleanup Test") {
                    steps {
                        echo("🧹 Testing memory cleanup...")
                        
                        // Procesar y limpiar en bucle
                        repeat(10) { iteration ->
                            // Crear datos temporales
                            val tempData = mapOf(
                                "iteration" to iteration,
                                "tempData" to (1..500).map { "temp_item_$it" },
                                "timestamp" to System.currentTimeMillis()
                            )
                            
                            writeJSON("temp/temp_$iteration.json", tempData)
                            val data = readJSON("temp/temp_$iteration.json")
                            val hash = md5("temp/temp_$iteration.json")
                            
                            // Simular limpieza (en un caso real se borrarían archivos temporales)
                            if (iteration % 3 == 0) {
                                echo("Cleanup iteration $iteration")
                            }
                        }
                        
                        echo("🔄 Memory cleanup test completed")
                    }
                }
            }
            
            post {
                always {
                    echo("📈 Generating memory usage report...")
                    
                    val runtime = Runtime.getRuntime()
                    val memoryInfo = mapOf(
                        "totalMemory" to runtime.totalMemory(),
                        "freeMemory" to runtime.freeMemory(),
                        "usedMemory" to (runtime.totalMemory() - runtime.freeMemory()),
                        "maxMemory" to runtime.maxMemory()
                    )
                    
                    writeJSON("memory-report.json", memoryInfo)
                    
                    val usedMemoryMB = memoryInfo["usedMemory"] as Long / (1024 * 1024)
                    echo("Memory used: ${usedMemoryMB}MB")
                }
            }
        }
        
        // When
        val executor = PipelineExecutor(
            stepExecutorRegistry = stepExecutorRegistry,
            extensionRegistry = extensionRegistry
        )
        
        executor.execute(pipeline, pipelineContext)
        waitForCompletion(3000)
        
        // Then
        assertOutputContains("🧠 Starting memory intensive operations...")
        assertOutputContains("📄 Large JSON files created")
        assertOutputContains("⚡ Processing large files in parallel...")
        assertOutputContains("✅ Parallel processing completed")
        assertOutputContains("🧹 Testing memory cleanup...")
        assertOutputContains("📈 Generating memory usage report...")
        
        // Verificar archivos grandes
        assertFileExists("large/data_0.json")
        assertFileExists("large/data_4.json")
        assertFileExists("large-data-archive.zip")
        assertFileExists("memory-report.json")
        
        // Verificar que los archivos grandes realmente son grandes
        val largeFile = workingDirectory.resolve("large/data_0.json")
        assertTrue(largeFile.length() > 10000, "Large file should be substantial, was: ${largeFile.length()} bytes")
        
        assertNoErrors()
    }
    
    @Test
    fun `concurrent pipeline execution should be thread-safe`() = runBlocking {
        // Given - Múltiples pipelines ejecutándose concurrentemente
        val pipelineTemplate = { id: Int ->
            pipeline("concurrent-pipeline-$id") {
                description("Concurrent execution test pipeline $id")
                
                stages {
                    stage("Stage A") {
                        steps {
                            echo("Pipeline $id - Stage A starting")
                            writeFile("pipeline_$id/stage_a.txt", "Stage A output for pipeline $id")
                            sleep(1) // Simular trabajo
                            echo("Pipeline $id - Stage A completed")
                        }
                    }
                    
                    stage("Stage B") {
                        steps {
                            echo("Pipeline $id - Stage B starting")
                            
                            val data = mapOf(
                                "pipelineId" to id,
                                "stage" to "B",
                                "timestamp" to System.currentTimeMillis(),
                                "items" to (1..50).map { "item_$it" }
                            )
                            
                            writeJSON("pipeline_$id/data.json", data)
                            val readData = readJSON("pipeline_$id/data.json")
                            val hash = sha1("pipeline_$id/data.json")
                            
                            writeFile("pipeline_$id/stage_b.txt", "Stage B hash: $hash")
                            echo("Pipeline $id - Stage B completed")
                        }
                    }
                    
                    stage("Stage C") {
                        steps {
                            echo("Pipeline $id - Stage C starting")
                            
                            val exists_a = fileExists("pipeline_$id/stage_a.txt")
                            val exists_b = fileExists("pipeline_$id/stage_b.txt")
                            
                            writeJSON("pipeline_$id/summary.json", mapOf(
                                "pipelineId" to id,
                                "stageACompleted" to exists_a,
                                "stageBCompleted" to exists_b,
                                "finalTimestamp" to System.currentTimeMillis()
                            ))
                            
                            echo("Pipeline $id - Stage C completed")
                        }
                    }
                }
            }
        }
        
        val concurrentPipelines = 5
        val startTime = System.currentTimeMillis()
        
        // When - Ejecutar pipelines concurrentemente
        val jobs = (1..concurrentPipelines).map { pipelineId ->
            async {
                val pipeline = pipelineTemplate(pipelineId)
                val executor = PipelineExecutor(
                    stepExecutorRegistry = stepExecutorRegistry,
                    extensionRegistry = extensionRegistry
                )
                
                // Crear contexto independiente para cada pipeline
                val independentContext = PipelineContext(
                    jobId = "concurrent-job-$pipelineId",
                    workerId = "worker-$pipelineId",
                    workingDirectory = workingDirectory,
                    environment = mutableMapOf("PIPELINE_ID" to pipelineId.toString()),
                    outputChannel = outputChannel,
                    eventChannel = eventChannel,
                    libraryManager = pipelineContext.libraryManager,
                    securityManager = pipelineContext.securityManager,
                    commandExecutor = pipelineContext.commandExecutor
                )
                
                executor.execute(pipeline, independentContext)
                pipelineId
            }
        }
        
        // Esperar a que todos completen
        val completedPipelines = awaitAll(*jobs.toTypedArray())
        val totalTime = System.currentTimeMillis() - startTime
        
        waitForCompletion(1000)
        
        // Then - Verificar que todos los pipelines completaron correctamente
        assertEquals(concurrentPipelines, completedPipelines.size)
        println("Executed $concurrentPipelines pipelines concurrently in ${totalTime}ms")
        
        // Verificar que cada pipeline creó sus archivos
        repeat(concurrentPipelines) { i ->
            val pipelineId = i + 1
            assertFileExists("pipeline_$pipelineId/stage_a.txt")
            assertFileExists("pipeline_$pipelineId/stage_b.txt")
            assertFileExists("pipeline_$pipelineId/data.json")
            assertFileExists("pipeline_$pipelineId/summary.json")
            
            // Verificar contenido específico de cada pipeline
            assertFileContent("pipeline_$pipelineId/stage_a.txt") { 
                it.contains("Stage A output for pipeline $pipelineId") 
            }
            assertFileContent("pipeline_$pipelineId/data.json") { 
                it.contains("\"pipelineId\": $pipelineId") 
            }
        }
        
        // Verificar que el output contiene mensajes de todos los pipelines
        val output = getOutputText()
        repeat(concurrentPipelines) { i ->
            val pipelineId = i + 1
            assertTrue(output.contains("Pipeline $pipelineId - Stage A starting"))
            assertTrue(output.contains("Pipeline $pipelineId - Stage C completed"))
        }
        
        // El tiempo total debería ser menor que ejecutar secuencialmente
        assertTrue(totalTime < concurrentPipelines * 5000, 
            "Concurrent execution should be faster than sequential. Took: ${totalTime}ms")
        
        assertNoErrors()
    }
}