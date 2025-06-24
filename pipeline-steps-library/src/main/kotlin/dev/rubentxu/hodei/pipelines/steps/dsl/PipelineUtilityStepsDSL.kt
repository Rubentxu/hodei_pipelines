package dev.rubentxu.hodei.pipelines.steps.dsl

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionStep

/**
 * DSL Extensions para pipeline-utility-steps compatible con Jenkins.
 * Proporciona sintaxis natural para operaciones con archivos, JSON, XML, etc.
 */

/**
 * Find files matching glob pattern.
 */
@PipelineDslMarker
fun StepsBuilder.findFiles(
    glob: String = "**/*",
    excludes: String? = null
): String {
    val stepName = "findFiles-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "findFiles",
            parameters = mapOf(
                "glob" to glob,
                "excludes" to (excludes ?: "")
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Read JSON from file or text.
 */
@PipelineDslMarker
fun StepsBuilder.readJSON(file: String): String {
    val stepName = "readJSON-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readJSON",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.readJSON(text: String, fromText: Boolean): String {
    val stepName = "readJSON-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readJSON",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Write JSON to file.
 */
@PipelineDslMarker
fun StepsBuilder.writeJSON(
    file: String,
    json: Any,
    pretty: Boolean = true
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeJSON",
            parameters = mapOf(
                "file" to file,
                "json" to json,
                "pretty" to pretty
            )
        )
    )
}

/**
 * Read YAML from file or text.
 */
@PipelineDslMarker
fun StepsBuilder.readYaml(file: String): String {
    val stepName = "readYaml-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readYaml",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.readYaml(text: String, fromText: Boolean): String {
    val stepName = "readYaml-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readYaml",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Write YAML to file.
 */
@PipelineDslMarker
fun StepsBuilder.writeYaml(
    file: String,
    data: Any
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeYaml",
            parameters = mapOf(
                "file" to file,
                "data" to data
            )
        )
    )
}

/**
 * Read CSV file.
 */
@PipelineDslMarker
fun StepsBuilder.readCSV(
    file: String,
    format: String = "DEFAULT"
): String {
    val stepName = "readCSV-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readCSV",
            parameters = mapOf(
                "file" to file,
                "format" to format
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Write CSV file.
 */
@PipelineDslMarker
fun StepsBuilder.writeCSV(
    file: String,
    records: List<Map<String, Any>>,
    format: String = "DEFAULT"
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeCSV",
            parameters = mapOf(
                "file" to file,
                "records" to records,
                "format" to format
            )
        )
    )
}

/**
 * Read manifest file.
 */
@PipelineDslMarker
fun StepsBuilder.readManifest(file: String = "META-INF/MANIFEST.MF"): String {
    val stepName = "readManifest-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readManifest",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

/**
 * Read properties file or text.
 */
@PipelineDslMarker
fun StepsBuilder.readProperties(
    file: String,
    defaults: Map<String, String>? = null,
    interpolate: Boolean = false
): String {
    val stepName = "readProperties-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readProperties",
            parameters = mapOf(
                "file" to file,
                "defaults" to (defaults ?: emptyMap<String, String>()),
                "interpolate" to interpolate
            ),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.readProperties(
    text: String,
    fromText: Boolean,
    defaults: Map<String, String>? = null,
    interpolate: Boolean = false
): String {
    val stepName = "readProperties-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readProperties",
            parameters = mapOf(
                "text" to text,
                "defaults" to (defaults ?: emptyMap<String, String>()),
                "interpolate" to interpolate
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Write properties file.
 */
@PipelineDslMarker
fun StepsBuilder.writeProperties(
    file: String,
    properties: Map<String, String>,
    comment: String? = null
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeProperties",
            parameters = mapOf(
                "file" to file,
                "properties" to properties,
                "comment" to (comment ?: "")
            )
        )
    )
}

/**
 * Create ZIP archive.
 */
@PipelineDslMarker
fun StepsBuilder.zip(
    zipFile: String,
    glob: String = "**/*",
    exclude: String? = null,
    dir: String? = null
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "zip",
            parameters = mapOf(
                "zipFile" to zipFile,
                "glob" to glob,
                "exclude" to (exclude ?: ""),
                "dir" to (dir ?: "")
            )
        )
    )
}

/**
 * Extract ZIP archive.
 */
@PipelineDslMarker
fun StepsBuilder.unzip(
    zipFile: String,
    dir: String? = null,
    glob: String? = null,
    read: Boolean = false,
    test: Boolean = false,
    quiet: Boolean = false
): String? {
    val stepName = if (read || test) "unzip-${System.currentTimeMillis()}" else null
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "unzip",
            parameters = mapOf(
                "zipFile" to zipFile,
                "dir" to (dir ?: ""),
                "glob" to (glob ?: ""),
                "read" to read,
                "test" to test,
                "quiet" to quiet
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Create TAR archive.
 */
@PipelineDslMarker
fun StepsBuilder.tar(
    file: String,
    glob: String = "**/*",
    exclude: String? = null,
    compress: Boolean = false,
    dir: String? = null
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "tar",
            parameters = mapOf(
                "file" to file,
                "glob" to glob,
                "exclude" to (exclude ?: ""),
                "compress" to compress,
                "dir" to (dir ?: "")
            )
        )
    )
}

/**
 * Extract TAR archive.
 */
@PipelineDslMarker
fun StepsBuilder.untar(
    file: String,
    dir: String? = null,
    glob: String? = null,
    quiet: Boolean = false
): String {
    val stepName = "untar-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "untar",
            parameters = mapOf(
                "file" to file,
                "dir" to (dir ?: ""),
                "glob" to (glob ?: ""),
                "quiet" to quiet
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Base64 encode data.
 */
@PipelineDslMarker
fun StepsBuilder.base64Encode(file: String): String {
    val stepName = "base64Encode-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "base64Encode",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.base64Encode(text: String, fromText: Boolean): String {
    val stepName = "base64Encode-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "base64Encode",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Base64 decode data.
 */
@PipelineDslMarker
fun StepsBuilder.base64Decode(
    data: String,
    file: String? = null
): String? {
    val stepName = if (file == null) "base64Decode-${System.currentTimeMillis()}" else null
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "base64Decode",
            parameters = mapOf(
                "data" to data,
                "file" to (file ?: "")
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Calculate SHA1 hash.
 */
@PipelineDslMarker
fun StepsBuilder.sha1(file: String): String {
    val stepName = "sha1-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha1",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.sha1(text: String, fromText: Boolean): String {
    val stepName = "sha1-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha1",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Calculate SHA256 hash.
 */
@PipelineDslMarker
fun StepsBuilder.sha256(file: String): String {
    val stepName = "sha256-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha256",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.sha256(text: String, fromText: Boolean): String {
    val stepName = "sha256-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "sha256",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Calculate MD5 hash.
 */
@PipelineDslMarker
fun StepsBuilder.md5(file: String): String {
    val stepName = "md5-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "md5",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

@PipelineDslMarker
fun StepsBuilder.md5(text: String, fromText: Boolean): String {
    val stepName = "md5-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "md5",
            parameters = mapOf("text" to text),
            name = stepName
        )
    )
    return stepName
}

/**
 * Make HTTP request.
 */
@PipelineDslMarker
fun StepsBuilder.httpRequest(
    url: String,
    httpMode: String = "GET",
    acceptType: String = "APPLICATION_JSON",
    contentType: String? = null,
    requestBody: String? = null,
    authentication: String? = null,
    timeout: Int = 30
): String {
    val stepName = "httpRequest-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "httpRequest",
            parameters = mapOf(
                "url" to url,
                "httpMode" to httpMode,
                "acceptType" to acceptType,
                "contentType" to (contentType ?: ""),
                "requestBody" to (requestBody ?: ""),
                "authentication" to (authentication ?: ""),
                "timeout" to timeout
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * HTTP request with DSL configuration.
 */
@PipelineDslMarker
inline fun StepsBuilder.httpRequest(
    url: String,
    block: HttpRequestBuilder.() -> Unit
): String {
    val builder = HttpRequestBuilder(url)
    builder.block()
    return builder.addToSteps(this)
}

@PipelineDslMarker
class HttpRequestBuilder(private val url: String) {
    var httpMode: String = "GET"
    var acceptType: String = "APPLICATION_JSON"
    var contentType: String? = null
    var requestBody: String? = null
    var authentication: String? = null
    var timeout: Int = 30
    
    fun get() { httpMode = "GET" }
    fun post() { httpMode = "POST" }
    fun put() { httpMode = "PUT" }
    fun delete() { httpMode = "DELETE" }
    fun patch() { httpMode = "PATCH" }
    
    fun json() { acceptType = "APPLICATION_JSON" }
    fun xml() { acceptType = "APPLICATION_XML" }
    fun text() { acceptType = "TEXT_PLAIN" }
    
    fun body(content: String, type: String = "application/json") {
        requestBody = content
        contentType = type
    }
    
    fun auth(credentials: String) {
        authentication = credentials
    }
    
    internal fun addToSteps(stepsBuilder: StepsBuilder): String {
        return stepsBuilder.httpRequest(
            url = url,
            httpMode = httpMode,
            acceptType = acceptType,
            contentType = contentType,
            requestBody = requestBody,
            authentication = authentication,
            timeout = timeout
        )
    }
}

/**
 * Compare version strings.
 */
@PipelineDslMarker
fun StepsBuilder.compareVersions(
    v1: String,
    v2: String,
    failIfEmpty: Boolean = false
): String {
    val stepName = "compareVersions-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "compareVersions",
            parameters = mapOf(
                "v1" to v1,
                "v2" to v2,
                "failIfEmpty" to failIfEmpty
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Get nodes by label.
 */
@PipelineDslMarker
fun StepsBuilder.nodesByLabel(
    label: String,
    offline: Boolean = false
): String {
    val stepName = "nodesByLabel-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "nodesByLabel",
            parameters = mapOf(
                "label" to label,
                "offline" to offline
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Load library resource.
 */
@PipelineDslMarker
fun StepsBuilder.libraryResource(
    resource: String,
    encoding: String = "UTF-8"
): String {
    val stepName = "libraryResource-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "libraryResource",
            parameters = mapOf(
                "resource" to resource,
                "encoding" to encoding
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Touch file (create or update timestamp).
 */
@PipelineDslMarker
fun StepsBuilder.touch(
    file: String,
    timestamp: Long? = null
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "touch",
            parameters = mapOf(
                "file" to file,
                "timestamp" to (timestamp ?: 0L)
            )
        )
    )
}

/**
 * Read trusted file.
 */
@PipelineDslMarker
fun StepsBuilder.readTrusted(
    file: String,
    encoding: String = "UTF-8"
): String {
    val stepName = "readTrusted-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "readTrusted",
            parameters = mapOf(
                "file" to file,
                "encoding" to encoding
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Write trusted file.
 */
@PipelineDslMarker
fun StepsBuilder.writeTrusted(
    file: String,
    text: String,
    encoding: String = "UTF-8"
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "pipeline-utility-steps",
            action = "writeTrusted",
            parameters = mapOf(
                "file" to file,
                "text" to text,
                "encoding" to encoding
            )
        )
    )
}

// Helper function para agregar extension steps
private fun StepsBuilder.addExtensionStep(step: ExtensionStep) {
    // Usar reflexión para agregar el step a la lista interna
    val stepsField = this::class.java.getDeclaredField("steps")
    stepsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val steps = stepsField.get(this) as MutableList<dev.rubentxu.hodei.pipelines.dsl.model.Step>
    steps.add(step)
}