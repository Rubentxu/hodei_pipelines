package dev.rubentxu.hodei.pipelines.steps.e2e

import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionStep
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests E2E para pipeline-utility-steps.
 * Verifica que todos los utility steps funcionan correctamente de extremo a extremo.
 */
class PipelineUtilityStepsE2ETest : E2ETestBase() {
    
    @Test
    fun `findFiles should locate files by pattern`() = runBlocking {
        // Given
        createTestFiles(mapOf(
            "src/main.kt" to "fun main() {}",
            "src/utils.kt" to "object Utils",
            "docs/README.md" to "# Documentation",
            "test.txt" to "test content",
            "build.gradle.kts" to "plugins { kotlin(\"jvm\") }"
        ))
        
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "findFiles",
            parameters = mapOf(
                "glob" to "**/*.kt",
                "excludes" to ""
            ),
            name = "findKotlinFiles"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Finding files with pattern: **/*.kt")
        assertOutputContains("Found 2 files")
        
        val foundFiles = getVariable<List<Map<String, Any>>>("findKotlinFiles.result")
        assertNotNull(foundFiles)
        assertEquals(2, foundFiles.size)
        
        val fileNames = foundFiles.map { it["name"] as String }.sorted()
        assertEquals(listOf("main.kt", "utils.kt"), fileNames)
    }
    
    @Test
    fun `readJSON and writeJSON should work with JSON data`() = runBlocking {
        // Given
        val jsonData = mapOf(
            "name" to "TestApp",
            "version" to "1.0.0",
            "dependencies" to listOf("kotlin-stdlib", "kotlinx-coroutines"),
            "config" to mapOf(
                "debug" to true,
                "port" to 8080
            )
        )
        
        val writeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeJSON",
            parameters = mapOf(
                "file" to "config.json",
                "json" to jsonData,
                "pretty" to true
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readJSON",
            parameters = mapOf("file" to "config.json"),
            name = "readConfig"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists("config.json")
        assertOutputContains("Writing JSON to file: config.json")
        assertOutputContains("JSON written successfully")
        assertOutputContains("Reading JSON from file: config.json")
        assertOutputContains("JSON parsed successfully")
        
        val readData = getVariable<Map<String, Any>>("readConfig.result")
        assertNotNull(readData)
        assertEquals("TestApp", readData["name"])
        assertEquals("1.0.0", readData["version"])
    }
    
    @Test
    fun `readYaml and writeYaml should work with YAML data`() = runBlocking {
        // Given
        val yamlData = mapOf(
            "apiVersion" to "apps/v1",
            "kind" to "Deployment",
            "metadata" to mapOf(
                "name" to "myapp",
                "namespace" to "default"
            ),
            "spec" to mapOf(
                "replicas" to 3,
                "selector" to mapOf(
                    "matchLabels" to mapOf("app" to "myapp")
                )
            )
        )
        
        val writeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeYaml",
            parameters = mapOf(
                "file" to "deployment.yaml",
                "data" to yamlData
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readYaml",
            parameters = mapOf("file" to "deployment.yaml"),
            name = "readDeployment"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists("deployment.yaml")
        assertOutputContains("Writing YAML to file: deployment.yaml")
        assertOutputContains("YAML written successfully")
        assertOutputContains("Reading YAML from file: deployment.yaml")
        assertOutputContains("YAML parsed successfully")
        
        val readData = getVariable<Map<String, Any>>("readDeployment.result")
        assertNotNull(readData)
        assertEquals("apps/v1", readData["apiVersion"])
        assertEquals("Deployment", readData["kind"])
    }
    
    @Test
    fun `readCSV and writeCSV should work with CSV data`() = runBlocking {
        // Given
        val csvData = listOf(
            mapOf("id" to "1", "name" to "John Doe", "email" to "john@example.com"),
            mapOf("id" to "2", "name" to "Jane Smith", "email" to "jane@example.com"),
            mapOf("id" to "3", "name" to "Bob Johnson", "email" to "bob@example.com")
        )
        
        val writeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeCSV",
            parameters = mapOf(
                "file" to "users.csv",
                "records" to csvData,
                "format" to "DEFAULT"
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readCSV",
            parameters = mapOf(
                "file" to "users.csv",
                "format" to "DEFAULT"
            ),
            name = "readUsers"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists("users.csv")
        assertOutputContains("Writing CSV to file: users.csv")
        assertOutputContains("CSV written successfully - 3 records")
        assertOutputContains("Reading CSV from file: users.csv")
        assertOutputContains("CSV parsed successfully - 3 records")
        
        val readData = getVariable<List<Map<String, String>>>("readUsers.result")
        assertNotNull(readData)
        assertEquals(3, readData.size)
        assertEquals("John Doe", readData[0]["name"])
    }
    
    @Test
    fun `readProperties and writeProperties should work with properties files`() = runBlocking {
        // Given
        val properties = mapOf(
            "app.name" to "MyApplication",
            "app.version" to "1.0.0",
            "server.port" to "8080",
            "database.url" to "jdbc:postgresql://localhost/mydb",
            "debug.enabled" to "true"
        )
        
        val writeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeProperties",
            parameters = mapOf(
                "file" to "app.properties",
                "properties" to properties,
                "comment" to "Application configuration"
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readProperties",
            parameters = mapOf(
                "file" to "app.properties",
                "interpolate" to false
            ),
            name = "readAppProps"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists("app.properties")
        assertOutputContains("Writing properties to file: app.properties")
        assertOutputContains("Properties written successfully - 5 properties")
        assertOutputContains("Reading properties from file: app.properties")
        assertOutputContains("Properties read successfully - 5 properties")
        
        val readData = getVariable<Map<String, String>>("readAppProps.result")
        assertNotNull(readData)
        assertEquals("MyApplication", readData["app.name"])
        assertEquals("8080", readData["server.port"])
    }
    
    @Test
    fun `zip and unzip should work with archives`() = runBlocking {
        // Given
        createTestFiles(mapOf(
            "src/main.kt" to "fun main() { println(\"Hello World\") }",
            "src/utils.kt" to "object Utils { fun helper() = \"help\" }",
            "README.md" to "# My Project\nThis is a test project",
            "config.json" to """{"name": "test", "version": "1.0"}"""
        ))
        
        val zipStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "zip",
            parameters = mapOf(
                "zipFile" to "archive.zip",
                "glob" to "**/*",
                "exclude" to ""
            )
        )
        
        val unzipStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "unzip",
            parameters = mapOf(
                "zipFile" to "archive.zip",
                "dir" to "extracted",
                "test" to false,
                "quiet" to false
            ),
            name = "unzipFiles"
        )
        
        // When
        executeStep(zipStep)
        executeStep(unzipStep)
        waitForCompletion()
        
        // Then
        assertFileExists("archive.zip")
        assertOutputContains("Creating ZIP archive: archive.zip")
        assertOutputContains("ZIP archive created successfully")
        assertOutputContains("Extracting ZIP archive: archive.zip")
        
        val extractedFiles = getVariable<List<String>>("unzipFiles.result")
        assertNotNull(extractedFiles)
        assertTrue(extractedFiles.isNotEmpty())
    }
    
    @Test
    fun `base64Encode and base64Decode should work with data`() = runBlocking {
        // Given
        val originalContent = "Hello, World! This is a test message for Base64 encoding."
        createTestFile("input.txt", originalContent)
        
        val encodeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "base64Encode",
            parameters = mapOf("file" to "input.txt"),
            name = "encodeData"
        )
        
        val decodeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "base64Decode",
            parameters = mapOf(
                "data" to "SGVsbG8sIFdvcmxkISBUaGlzIGlzIGEgdGVzdCBtZXNzYWdlIGZvciBCYXNlNjQgZW5jb2Rpbmcu", // Base64 for the message
                "file" to "decoded.txt"
            )
        )
        
        // When
        executeStep(encodeStep)
        executeStep(decodeStep)
        waitForCompletion()
        
        // Then
        assertOutputContains("Base64 encoding file: input.txt")
        assertOutputContains("Base64 encoding completed")
        assertOutputContains("Base64 decoding data")
        assertOutputContains("Decoded data written to file: decoded.txt")
        
        val encodedData = getVariable<String>("encodeData.result")
        assertNotNull(encodedData)
        assertTrue(encodedData.isNotEmpty())
        
        assertFileExists("decoded.txt")
    }
    
    @Test
    fun `hash functions should calculate correct hashes`() = runBlocking {
        // Given
        val content = "Hello, World!"
        createTestFile("test.txt", content)
        
        val sha1Step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha1",
            parameters = mapOf("file" to "test.txt"),
            name = "sha1Hash"
        )
        
        val sha256Step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha256",
            parameters = mapOf("file" to "test.txt"),
            name = "sha256Hash"
        )
        
        val md5Step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "md5",
            parameters = mapOf("file" to "test.txt"),
            name = "md5Hash"
        )
        
        // When
        executeStep(sha1Step)
        executeStep(sha256Step)
        executeStep(md5Step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Computing SHA1 hash of file: test.txt")
        assertOutputContains("Computing SHA256 hash of file: test.txt")
        assertOutputContains("Computing MD5 hash of file: test.txt")
        
        val sha1Hash = getVariable<String>("sha1Hash.result")
        val sha256Hash = getVariable<String>("sha256Hash.result")
        val md5Hash = getVariable<String>("md5Hash.result")
        
        assertNotNull(sha1Hash)
        assertNotNull(sha256Hash)
        assertNotNull(md5Hash)
        
        // Verify hash lengths (SHA1=40, SHA256=64, MD5=32 hex chars)
        assertEquals(40, sha1Hash.length)
        assertEquals(64, sha256Hash.length)
        assertEquals(32, md5Hash.length)
    }
    
    @Test
    fun `httpRequest should make HTTP calls`() = runBlocking {
        // Given
        val url = "https://httpbin.org/get"
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "httpRequest",
            parameters = mapOf(
                "url" to url,
                "httpMode" to "GET",
                "acceptType" to "APPLICATION_JSON",
                "timeout" to 30
            ),
            name = "httpCall"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("HTTP GET request to: $url")
        assertOutputContains("Request method: GET")
        assertOutputContains("Accept type: APPLICATION_JSON")
        assertOutputContains("HTTP request completed - Status: 200")
        
        val response = getVariable<Map<String, Any>>("httpCall.result")
        assertNotNull(response)
        assertEquals(200, response["status"])
    }
    
    @Test
    fun `compareVersions should compare version strings correctly`() = runBlocking {
        // Given
        val v1 = "1.2.3"
        val v2 = "1.2.4"
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "compareVersions",
            parameters = mapOf(
                "v1" to v1,
                "v2" to v2,
                "failIfEmpty" to false
            ),
            name = "versionCompare"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Comparing versions: $v1 vs $v2")
        assertOutputContains("Result: $v1 < $v2")
        
        val result = getVariable<Int>("versionCompare.result")
        assertNotNull(result)
        assertTrue(result < 0, "v1 should be less than v2")
    }
    
    @Test
    fun `nodesByLabel should find nodes`() = runBlocking {
        // Given
        val label = "linux"
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "nodesByLabel",
            parameters = mapOf(
                "label" to label,
                "offline" to false
            ),
            name = "findNodes"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Finding nodes with label: $label")
        assertOutputContains("Found")
        assertOutputContains("nodes:")
        
        val nodes = getVariable<List<String>>("findNodes.result")
        assertNotNull(nodes)
        assertTrue(nodes.isNotEmpty())
    }
    
    @Test
    fun `touch should create and update file timestamps`() = runBlocking {
        // Given
        val fileName = "timestamp.txt"
        val customTimestamp = System.currentTimeMillis() - 10000 // 10 seconds ago
        
        val touchStep1 = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "touch",
            parameters = mapOf("file" to fileName)
        )
        
        val touchStep2 = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "touch",
            parameters = mapOf(
                "file" to "custom-timestamp.txt",
                "timestamp" to customTimestamp
            )
        )
        
        // When
        executeStep(touchStep1)
        executeStep(touchStep2)
        waitForCompletion()
        
        // Then
        assertFileExists(fileName)
        assertFileExists("custom-timestamp.txt")
        assertOutputContains("Touching file: $fileName")
        assertOutputContains("File created")
        assertOutputContains("Timestamp updated to current time")
        assertOutputContains("Timestamp updated to:")
    }
    
    @Test
    fun `readTrusted and writeTrusted should handle sensitive data`() = runBlocking {
        // Given
        val sensitiveContent = """
            API_KEY=super-secret-key-12345
            DATABASE_PASSWORD=very-secure-password
            JWT_SECRET=ultra-secret-jwt-token
        """.trimIndent()
        
        val writeStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeTrusted",
            parameters = mapOf(
                "file" to "secrets.txt",
                "text" to sensitiveContent,
                "encoding" to "UTF-8"
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readTrusted",
            parameters = mapOf(
                "file" to "secrets.txt",
                "encoding" to "UTF-8"
            ),
            name = "readSecrets"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists("secrets.txt")
        assertOutputContains("Writing trusted file: secrets.txt")
        assertOutputContains("Trusted file written successfully")
        assertOutputContains("Reading trusted file: secrets.txt")
        assertOutputContains("Trusted file read successfully")
        
        val readContent = getVariable<String>("readSecrets.result")
        assertEquals(sensitiveContent, readContent)
    }
    
    @Test
    fun `libraryResource should load resources`() = runBlocking {
        // Given
        val resourceName = "scripts/deploy.sh"
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "libraryResource",
            parameters = mapOf(
                "resource" to resourceName,
                "encoding" to "UTF-8"
            ),
            name = "loadResource"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Loading library resource: $resourceName")
        assertOutputContains("Resource loaded successfully")
        
        val resourceContent = getVariable<String>("loadResource.result")
        assertNotNull(resourceContent)
        assertTrue(resourceContent.contains("Library resource: $resourceName"))
    }
    
    @Test
    fun `readManifest should parse JAR manifest`() = runBlocking {
        // Given - Create a simple manifest file
        val manifestContent = """
            Manifest-Version: 1.0
            Implementation-Title: Test Application
            Implementation-Version: 1.0.0
            Implementation-Vendor: Test Company
            Main-Class: com.example.Main
        """.trimIndent()
        
        createTestFile("META-INF/MANIFEST.MF", manifestContent)
        
        val step = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readManifest",
            parameters = mapOf("file" to "META-INF/MANIFEST.MF"),
            name = "readManifest"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Reading manifest from: META-INF/MANIFEST.MF")
        assertOutputContains("Manifest read successfully")
        
        val manifest = getVariable<Map<String, String>>("readManifest.result")
        assertNotNull(manifest)
        assertTrue(manifest.isNotEmpty())
    }
    
    @Test
    fun `tar and untar should work with TAR archives`() = runBlocking {
        // Given
        createTestFiles(mapOf(
            "app/main.py" to "print('Hello from Python')",
            "app/utils.py" to "def helper(): return 'help'",
            "docs/README.txt" to "Documentation file"
        ))
        
        val tarStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "tar",
            parameters = mapOf(
                "file" to "backup.tar.gz",
                "glob" to "**/*",
                "compress" to true
            )
        )
        
        val untarStep = ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "untar",
            parameters = mapOf(
                "file" to "backup.tar.gz",
                "dir" to "restored",
                "quiet" to false
            ),
            name = "untarFiles"
        )
        
        // When
        executeStep(tarStep)
        executeStep(untarStep)
        waitForCompletion()
        
        // Then
        assertOutputContains("Creating TAR archive: backup.tar.gz")
        assertOutputContains("With GZIP compression")
        assertOutputContains("TAR archive created successfully")
        assertOutputContains("Extracting TAR archive: backup.tar.gz")
        assertOutputContains("TAR archive extracted successfully")
    }
}