package dev.rubentxu.hodei.packages.app.features.packages.routes

import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.command.UploadArtifactCommand
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.*
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.ports.FormatHandler
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.service.ArtifactServicePort
import dev.rubentxu.hodei.packages.artifacts.identityaccess.model.UserId
import dev.rubentxu.hodei.packages.artifacts.registrymanagement.model.RegistryId
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.pypiRoutes(
    artifactService: ArtifactServicePort,
    handlers: Map<ArtifactType, FormatHandler> // Necesario para generar HTML simple
) {

    // POST /pypi/{repoId}/ - Twine upload
    // Twine usa Basic Auth por defecto.
    authenticate("basicAuth") {
        post("/pypi/{repoId}/") { // La ruta estándar de PyPI es solo "/", el {repoId} es una adición.
            val repoIdStr = call.parameters["repoId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                "Repository ID is missing."
            )

            val principal = call.principal<UserIdPrincipal>()
            if (principal == null) {
                call.application.environment.log.warn("Principal not found in authenticated PyPI POST route.")
                return@post call.respond(HttpStatusCode.Unauthorized, "Authentication failed.")
            }
            val uploaderUserId = UserId(principal.name)

            try {
                val registryId = RegistryId(UUID.fromString(repoIdStr))
                val multipartData = call.receiveMultipart()
                var fileUploaded = false

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "unknown_pypi_file"
                            val fileBytes = part.streamProvider().readBytes()

                            // Twine también envía metadatos como campos de formulario.
                            // Estos podrían usarse como `providedUserMetadata`.
                            // Por ahora, el PythonFormatHandler debería extraerlos del sdist/wheel si es posible.
                            // TODO: Recolectar otros PartData.FormItem para `providedUserMetadata`

                            val command = UploadArtifactCommand(
                                registryId = registryId,
                                filename = fileName,
                                content = fileBytes,
                                artifactType = ArtifactType.PYPI, // O determinar por extensión si es necesario
                                providedUserMetadata = emptyMap(), // TODO: Llenar con FormItems
                                uploaderUserId = uploaderUserId
                            )

                            artifactService.uploadArtifact(command).fold(
                                onSuccess = {
                                    fileUploaded = true
                                    call.application.environment.log.info("PyPI package '$fileName' uploaded successfully to repo '$repoIdStr'.")
                                    // Twine no espera un cuerpo de respuesta detallado en éxito, solo un 200 OK.
                                },
                                onFailure = { exception ->
                                    call.application.environment.log.error(
                                        "Failed to upload PyPI package '$fileName': ${exception.message}",
                                        exception
                                    )
                                    // Si un archivo falla, ¿deberíamos detenernos o continuar con otros?
                                    // Por ahora, respondemos al primer error.
                                    val statusCode = when (exception) {
                                        is IllegalArgumentException -> HttpStatusCode.BadRequest
                                        is IllegalStateException -> HttpStatusCode.Conflict
                                        else -> HttpStatusCode.InternalServerError
                                    }
                                    // No podemos responder múltiples veces en un forEachPart.
                                    // Necesitamos manejar esto mejor si hay múltiples archivos.
                                    // Twine usualmente sube un archivo a la vez por solicitud, pero el protocolo lo permite.
                                    // Por simplicidad, asumimos un archivo por solicitud o el primero que falle.
                                    if (!call.response.isSent) {
                                        call.respond(statusCode, "Failed to upload ${fileName}: ${exception.message}")
                                    }
                                    return@forEachPart // Salir del forEachPart
                                }
                            )
                        }

                        is PartData.FormItem -> {
                            // Recolectar estos para `providedUserMetadata`
                            // call.application.environment.log.info("PyPI FormItem: ${part.name} = ${part.value}")
                        }

                        is PartData.BinaryItem -> {
                            // No esperado de Twine
                            part.dispose()
                        }

                        is PartData.BinaryChannelItem -> {
                            // No esperado de Twine
                            part.dispose()
                        }
                    }
                    part.dispose()
                    if (call.response.isSent) return@forEachPart // Si ya respondimos (por error), salir.
                }

                if (!call.response.isSent) {
                    if (fileUploaded) {
                        call.respond(HttpStatusCode.OK, "Package(s) received.")
                    } else {
                        // Esto podría pasar si no se envió ningún FileItem válido
                        call.respond(HttpStatusCode.BadRequest, "No package file found in upload.")
                    }
                }

            } catch (e: IllegalArgumentException) {
                call.application.environment.log.warn("Bad request for PyPI POST: ${e.message}", e)
                if (!call.response.isSent) call.respond(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
            } catch (e: Exception) {
                call.application.environment.log.error("Error processing PyPI package POST request: ${e.message}", e)
                if (!call.response.isSent) call.respond(
                    HttpStatusCode.InternalServerError,
                    "Failed to process package upload: ${e.message}"
                )
            }
        }
    }

    // GET /pypi/{repoId}/simple/{package}/ - Pip "simple" API (HTML)
    get("/pypi/{repoId}/simple/{packageName}/") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing.")
        val rawPackageName = call.parameters["packageName"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Package name is missing."
        )

        // PEP 503: Nombres de paquetes normalizados (lowercase, '-' y '.' a '_')
        val normalizedPackageName = rawPackageName.lowercase().replace("[-.]".toRegex(), "_")

        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))
            // Para PyPI, el "group" no se usa, solo el nombre del paquete.
            val result = artifactService.getAllVersions("", normalizedPackageName)

            result.fold(
                onSuccess = { artifactsInRepo ->
                    // Incluso si no hay artefactos, la API simple debe devolver un HTML vacío (o 404 según el servidor)
                    // Muchos servidores devuelven 200 con una página vacía si el paquete existe pero no tiene archivos,
                    // o 404 si el paquete no existe en absoluto.
                    // Aquí, si `artifactsInRepo` está vacío, el handler generará un HTML vacío o indicará "no files".

                    val pypiHandler = handlers[ArtifactType.PYPI]
                    if (pypiHandler == null) {
                        call.application.environment.log.error("PythonFormatHandler not found.")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Server configuration error: Python handler missing."
                        )
                        return@fold
                    }

                    val scheme = call.request.origin.scheme
                    val host = call.request.origin.serverHost
                    val port = call.request.origin.serverPort.let { if (it == 80 || it == 443) "" else ":$it" }
                    // La URL base para los archivos PyPI, ej: /pypi/{repoId}/packages/...
                    // El handler necesitará construir las URLs completas de los archivos.
                    val registryBaseUrl = "$scheme://$host$port/pypi/$repoIdStr" // O la ruta a los archivos de paquetes

                    pypiHandler.generateRepositoryMetadata(
                        null,
                        normalizedPackageName,
                        artifactsInRepo,
                        registryBaseUrl
                    ).fold(
                        onSuccess = { simpleHtml ->
                            if (artifactsInRepo.isEmpty() && !simpleHtml.contains("href")) { // Heurística simple para "no encontrado"
                                call.respondText(simpleHtml, ContentType.Text.Html, HttpStatusCode.NotFound)
                            } else {
                                call.respondText(simpleHtml, ContentType.Text.Html)
                            }
                        },
                        onFailure = { ex ->
                            call.application.environment.log.error(
                                "Failed to generate PyPI simple HTML: ${ex.message}",
                                ex
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                "Failed to generate package listing: ${ex.message}"
                            )
                        }
                    )
                },
                onFailure = { exception ->
                    call.application.environment.log.error(
                        "Failed to retrieve PyPI package versions for '$normalizedPackageName': ${exception.message}",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve package versions: ${exception.message}"
                    )
                }
            )
        } catch (e: IllegalArgumentException) {
            call.application.environment.log.warn("Bad request for PyPI simple GET: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid repository ID: ${e.message}")
        } catch (e: Exception) {
            call.application.environment.log.error("Error processing PyPI simple GET request: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to process package request: ${e.message}")
        }
    }

    // GET /pypi/{repoId}/packages/{python_version}/{first_letter}/{package_name}/{filename}
    // Esta es una de las estructuras de URL de PEP 503 para descargar archivos.
    // Simplificaremos a: GET /pypi/{repoId}/files/{filename} (asumiendo que filename es único o que lo encontramos por coordenadas)
    // O, para ser más precisos con ArtifactService, necesitamos GAV (o GAV-like para Python).
    // Python no tiene un "group" fuerte. El "name" es el nombre del paquete, y "version" es la versión.
    // El filename contiene name-version[-build_tag]-[-python_tag-abi_tag-platform_tag].whl o .tar.gz
    get("/pypi/{repoId}/files/{filename}") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing.")
        val filename =
            call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Filename is missing.")

        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))

            // El PythonFormatHandler debería extraer coordenadas (name, version, etc.) del filename.
            val pythonHandler =
                handlers[ArtifactType.PYPI] as? FormatHandler // O una interfaz más específica si es necesario
                    ?: return@get call.respond(HttpStatusCode.InternalServerError, "Python handler not configured.")

            // Necesitamos una forma de que el handler nos dé las coordenadas a partir del nombre de archivo.
            // O, si el `UploadArtifactCommand` usó este `filename` y `ArtifactService` lo almacenó así.
            // Asumamos que el `PythonFormatHandler` tiene un método para esto, o que `extractCoordinates`
            // puede funcionar solo con el nombre de archivo para obtener una "clave" de búsqueda.
            // Por ahora, la forma más simple es si `ArtifactRepository` puede buscar por `(registryId, filename)`.
            // Si no, necesitamos parsear `name` y `version` del `filename` para usar `getArtifact`.

            // Simplificación: Intentar parsear name y version del filename. Esto es complejo.
            // Ejemplo: my_package-1.0.0-py3-none-any.whl -> name=my_package, version=1.0.0
            // Ejemplo: my_package-1.0.tar.gz -> name=my_package, version=1.0
            // Esta lógica debería estar en PythonFormatHandler.
            val (packageName, packageVersion, extension) = parsePythonFilename(filename) // Necesitarías esta función
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Cannot parse package name and version from filename: $filename"
                )

            val domainCoordinates = ArtifactCoordinates(
                group = ArtifactGroup(""), // PyPI no tiene groups
                name = packageName,
                version = ArtifactVersion(packageVersion),
                extension = ArtifactExtension(extension),
            )

            artifactService.getArtifact(domainCoordinates).fold(
                onSuccess = { artifact ->
                    if (artifact != null) {
                        // Verificar si el filename del artefacto coincide (si hay clasificadores/tags en el nombre de archivo)
                        // El `artifact.packagingType` o un metadato podría ayudar.
                        // Por ahora, si las coordenadas coinciden, asumimos que es el archivo correcto.
                        // Una mejor validación sería comparar `artifact.metadata.checksums` si el cliente envía un hash.

                        artifactService.retrieveArtifactContent(registryId, artifact.contentHash).fold(
                            onSuccess = { contentBytes ->
                                val principal = call.principal<UserIdPrincipal>()
                                val downloadedBy = principal?.let { UserId(it.name) }
                                artifactService.downloadArtifact(
                                    artifactId = artifact.id,
                                    downloadedBy = downloadedBy,
                                    clientIp = call.request.origin.remoteHost,
                                    userAgent = call.request.headers[HttpHeaders.UserAgent]
                                )
                                call.respondBytes(contentBytes, ContentType.Application.OctetStream) // O más específico
                            },
                            onFailure = { ex ->
                                call.application.environment.log.error(
                                    "Failed to retrieve PyPI file content: ${ex.message}",
                                    ex
                                )
                                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve content.")
                            }
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File '$filename' not found.")
                    }
                },
                onFailure = { ex ->
                    call.application.environment.log.error("Failed to get PyPI file artifact: ${ex.message}", ex)
                    call.respond(HttpStatusCode.InternalServerError, "Error finding file.")
                }
            )

        } catch (e: IllegalArgumentException) {
            call.application.environment.log.warn("Bad request for PyPI file GET: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid request parameters: ${e.message}")
        } catch (e: Exception) {
            call.application.environment.log.error("Error processing PyPI file GET request: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to process file download: ${e.message}")
        }
    }
}


// Función helper muy simplificada para parsear nombres de archivo Python.
// ¡ESTO NECESITA UNA IMPLEMENTACIÓN MUY ROBUSTA, idealmente en PythonFormatHandler!
// Ver PEP 427 para nombres de wheel, y distutils/setuptools para sdist.
fun parsePythonFilename(filename: String): Triple<String, String, String>? {
    val extension = filename.substringAfterLast('.', "")
    val nameAndVersionPart = filename.substringBeforeLast(".$extension")

    // Caso wheel: my_package-1.0.0-py3-none-any.whl
    if (extension == "whl") {
        val parts = nameAndVersionPart.split('-')
        if (parts.size >= 2) {
            // Asumir que el nombre es la primera parte y la versión la segunda.
            // Esto es frágil si el nombre del paquete tiene guiones.
            // Una heurística mejor es buscar la primera parte que parezca una versión.
            var versionIndex = -1
            for (i in 1 until parts.size) { // Empezar desde el segundo elemento
                // Regex simple para versión (no completo para PEP 440)
                if (parts[i].matches(Regex("""^(\d+!)?\d+(\.\d+)*((a|b|rc)\d+)?(\.post\d+)?(\.dev\d+)?$"""))) {
                    versionIndex = i
                    break
                }
            }
            if (versionIndex > 0) {
                val name = parts.subList(0, versionIndex).joinToString("-")
                val version = parts[versionIndex]
                return Triple(name, version, extension)
            }
        }
    }
    // Caso sdist: my_package-1.0.tar.gz (extensión puede ser .zip también)
    else if (extension == "gz" && nameAndVersionPart.endsWith(".tar")) {
        val baseName = nameAndVersionPart.removeSuffix(".tar")
        val parts = baseName.split('-')
        if (parts.isNotEmpty()) {
            // Asumir que la última parte antes de la extensión es la versión.
            val version = parts.last()
            val name = parts.dropLast(1).joinToString("-")
            if (name.isNotEmpty() && version.isNotEmpty()) {
                // Validar que la versión parezca una versión
                if (version.matches(Regex("""^(\d+!)?\d+(\.\d+)*((a|b|rc)\d+)?(\.post\d+)?(\.dev\d+)?$"""))) {
                    return Triple(name, version, "$extension") // o "tar.gz"
                }
            }
        }
    } else if (extension == "zip") {
        // Lógica similar para .zip sdist
        val parts = nameAndVersionPart.split('-')
        if (parts.isNotEmpty()) {
            val version = parts.last()
            val name = parts.dropLast(1).joinToString("-")
            if (name.isNotEmpty() && version.isNotEmpty()) {
                if (version.matches(Regex("""^(\d+!)?\d+(\.\d+)*((a|b|rc)\d+)?(\.post\d+)?(\.dev\d+)?$"""))) {
                    return Triple(name, version, extension)
                }
            }
        }
    }


    return null // No se pudo parsear
}