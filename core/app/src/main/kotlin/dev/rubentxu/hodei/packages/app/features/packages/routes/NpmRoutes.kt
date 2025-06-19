package dev.rubentxu.hodei.packages.app.features.packages.routes

import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.command.UploadArtifactCommand
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.*
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.ports.FormatHandler
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.service.ArtifactServicePort
import dev.rubentxu.hodei.packages.artifacts.identityaccess.model.UserId
import dev.rubentxu.hodei.packages.artifacts.registrymanagement.model.RegistryId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

// Modelos simplificados para la publicación de NPM. El real es más complejo.
@Serializable
data class NpmPublicationRequest(
    val name: String, // e.g., "my-package" or "@scope/my-package"
    val versions: Map<String, NpmVersionData>,
    val _attachments: Map<String, NpmAttachmentData>, // e.g., "my-package-1.0.0.tgz"
    // ... otros campos como 'dist-tags', 'readme'
)

@Serializable
data class NpmVersionData(
    val name: String,
    val version: String,
    val description: String? = null,
    val main: String? = null,
    val dependencies: Map<String, String>? = null,
    val devDependencies: Map<String, String>? = null,
    val dist: NpmDistData,
    // ... muchos otros campos del package.json
)

@Serializable
data class NpmDistData(
    val shasum: String,
    val tarball: String // URL relativa o absoluta al tarball
)

@Serializable
data class NpmAttachmentData(
    val content_type: String,
    val data: String, // Base64 encoded tarball
    val length: Long
)


// Para login (esto es más para un AuthService)
@Serializable
data class NpmLoginRequest(val name: String?, val password: String?)

@Serializable
data class NpmLoginResponse(val token: String, val ok: Boolean, val username: String? = null, val email: String? = null)


fun Route.npmRoutes(
    artifactService: ArtifactServicePort,
    handlers: Map<ArtifactType, FormatHandler> // Necesario para generar JSON de paquete
    // authService: YourAuthService // Necesario para el login real
) {
    val json = Json { ignoreUnknownKeys = true } // Configura el parser JSON

    // PUT /npm/{repoId}/{packageName} - Publicar paquete
    // El cliente NPM hace PUT a /<packageName>, el {repoId} aquí es una adición.
    // Si el {repoId} es parte de la URL base del registry, entonces la ruta sería solo PUT /{packageName}
    // Asumimos que el cliente está configurado para incluir {repoId} en la URL.
    authenticate("bearerAuth") { // O la autenticación que use NPM (token en header)
        put("/npm/{repoId}/{packageName}") {
            val repoIdStr = call.parameters["repoId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                "Repository ID is missing."
            )
            val packageNameFromPath = call.parameters["packageName"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                "Package name from path is missing."
            )

            val principal = call.principal<UserIdPrincipal>() // Ajusta según tu UserIdPrincipal
            if (principal == null) {
                call.application.environment.log.warn("Principal not found in authenticated NPM PUT route.")
                return@put call.respond(HttpStatusCode.Unauthorized, "Authentication failed.")
            }
            val uploaderUserId = UserId(principal.name)

            try {
                val registryId = RegistryId(UUID.fromString(repoIdStr))
                val requestBody = call.receiveText()
                val npmPublicationData = json.decodeFromString<NpmPublicationRequest>(requestBody)

                // Validar que packageNameFromPath coincida con npmPublicationData.name
                if (packageNameFromPath != npmPublicationData.name) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        "Package name in path does not match package name in payload."
                    )
                }

                // NPM publica una versión a la vez, usualmente el attachment tiene el nombre del paquete y la versión.
                // El payload puede contener múltiples versiones, pero _attachments suele tener solo el tarball de la versión que se está publicando.
                if (npmPublicationData._attachments.isEmpty()) {
                    return@put call.respond(HttpStatusCode.BadRequest, "No attachments found in NPM publish request.")
                }

                // Tomamos el primer (y usualmente único) attachment
                val attachmentEntry = npmPublicationData._attachments.entries.first()
                val attachmentFilename = attachmentEntry.key // e.g., "my-package-1.0.0.tgz"
                val attachmentData = attachmentEntry.value

                val tarballBytes = Base64.getDecoder().decode(attachmentData.data)

                // Extraer metadatos del package.json dentro del tarball o del payload JSON
                // El NpmFormatHandler debería ser capaz de hacer esto.
                // Por ahora, pasamos algunos metadatos básicos si están disponibles.
                val versionToPublish = npmPublicationData.versions.values.firstOrNull { ver ->
                    attachmentFilename.contains(ver.version) // Intenta encontrar la versión correspondiente al tarball
                }

                val providedUserMetadata = mutableMapOf<String, String>()
                versionToPublish?.let {
                    providedUserMetadata["name"] = it.name
                    providedUserMetadata["version"] = it.version
                    it.description?.let { desc -> providedUserMetadata["description"] = desc }
                    // ... más metadatos del package.json
                }


                val command = UploadArtifactCommand(
                    registryId = registryId,
                    filename = attachmentFilename, // El nombre del .tgz
                    content = tarballBytes,
                    artifactType = ArtifactType.NPM,
                    providedUserMetadata = providedUserMetadata.ifEmpty { null },
                    uploaderUserId = uploaderUserId
                )

                artifactService.uploadArtifact(command).fold(
                    onSuccess = { artifact ->
                        // NPM espera un JSON de respuesta simple
                        call.respond(
                            HttpStatusCode.Created,
                            mapOf("ok" to true, "id" to artifact.coordinates.name, "rev" to artifact.id.value)
                        )
                    },
                    onFailure = { exception ->
                        call.application.environment.log.error(
                            "Failed to publish NPM package: ${exception.message}",
                            exception
                        )
                        val statusCode = when (exception) {
                            is IllegalArgumentException -> HttpStatusCode.BadRequest
                            is IllegalStateException -> HttpStatusCode.Conflict
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to "publish_error", "reason" to (exception.message ?: "Unknown error"))
                        )
                    }
                )

            } catch (e: kotlinx.serialization.SerializationException) {
                call.application.environment.log.warn("NPM PUT request with invalid JSON: ${e.message}", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "bad_request", "reason" to "Invalid JSON payload: ${e.message}")
                )
            } catch (e: IllegalArgumentException) { // Para RegistryId o decodificación Base64
                call.application.environment.log.warn("Bad request for NPM PUT: ${e.message}", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "bad_request", "reason" to "Invalid request: ${e.message}")
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Error processing NPM package PUT request: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "internal_error", "reason" to "Failed to process package publish: ${e.message}")
                )
            }
        }
    }

    // GET /npm/{repoId}/{package} - Obtener información del paquete (JSON)
    // El cliente NPM hace GET a /<packageName> o /@scope%2FpackageName
    get("/npm/{repoId}/{packageName}") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing.")
        // El packageName puede tener un scope, ej: @my-scope/my-package. Ktor lo decodifica.
        val rawPackageName = call.parameters["packageName"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Package name is missing."
        )
        // El cliente NPM puede hacer GET a /@scope%2Fpkg o /pkg. Si tiene scope, el path param será "@scope/pkg"
        // Si no tiene scope, será "pkg".
        // El group para ArtifactService sería el scope, y name sería el pkgName.

        val (group, name) = if (rawPackageName.startsWith("@") && rawPackageName.contains("/")) {
            rawPackageName.substringBeforeLast("/") to rawPackageName.substringAfterLast("/")
        } else {
            null to rawPackageName
        }


        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))
            val result = artifactService.getAllVersions(group ?: "", name) // group puede ser "" si no hay scope

            result.fold(
                onSuccess = { artifactsInRepo ->
                    if (artifactsInRepo.isEmpty()) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf(
                                "error" to "not_found",
                                "reason" to "Package '$rawPackageName' not found in repo '$repoIdStr'."
                            )
                        )
                        return@fold
                    }

                    val npmHandler = handlers[ArtifactType.NPM]
                    if (npmHandler == null) {
                        call.application.environment.log.error("NpmFormatHandler not found.")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Server configuration error: NPM handler missing."
                        )
                        return@fold
                    }

                    val scheme = call.request.origin.scheme
                    val host = call.request.origin.serverHost
                    val port = call.request.origin.serverPort.let { if (it == 80 || it == 443) "" else ":$it" }
                    // La URL base para los tarballs de NPM es un poco diferente, usualmente /<pkgName>/-/<pkgName>-<version>.tgz
                    // El handler debe saber cómo construir esto. Pasamos la base del registry.
                    val registryBaseUrl = "$scheme://$host$port/npm/$repoIdStr"


                    npmHandler.generateRepositoryMetadata(group, name, artifactsInRepo, registryBaseUrl).fold(
                        onSuccess = { packageJson ->
                            call.respondText(packageJson, ContentType.Application.Json)
                        },
                        onFailure = { ex ->
                            call.application.environment.log.error(
                                "Failed to generate NPM package JSON: ${ex.message}",
                                ex
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf(
                                    "error" to "internal_error",
                                    "reason" to "Failed to generate package data: ${ex.message}"
                                )
                            )
                        }
                    )
                },
                onFailure = { exception ->
                    call.application.environment.log.error(
                        "Failed to retrieve NPM package versions for '$rawPackageName': ${exception.message}",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError, mapOf(
                            "error" to "internal_error",
                            "reason" to "Failed to retrieve package versions: ${exception.message}"
                        )
                    )
                }
            )
        } catch (e: IllegalArgumentException) { // Para RegistryId
            call.application.environment.log.warn("Bad request for NPM GET package: ${e.message}", e)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "bad_request", "reason" to "Invalid repository ID: ${e.message}")
            )
        } catch (e: Exception) {
            call.application.environment.log.error("Error processing NPM GET package request: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "internal_error", "reason" to "Failed to process package request: ${e.message}")
            )
        }
    }

    // GET /npm/{repoId}/{packageName}/-/{tarballFilename} - Descargar tarball
    // Ej: /npm/myrepo/my-package/-/my-package-1.0.0.tgz
    // O para scopes: /npm/myrepo/@my-scope/my-package/-/my-package-1.0.0.tgz
    get("/npm/{repoId}/{packageNameWithScope...}/-/{tarballFilename}") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing.")
        val packageNameParts = call.parameters.getAll("packageNameWithScope") ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Package name is missing."
        )
        val tarballFilename = call.parameters["tarballFilename"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Tarball filename is missing."
        )

        // Reconstruir packageNameWithScope, puede ser "pkg" o "@scope/pkg"
        val rawPackageName = packageNameParts.joinToString("/")


        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))

            // El NpmFormatHandler debería ser capaz de parsear las coordenadas del tarballFilename
            // O podemos inferirlas aquí si el formato es estándar: <name>-<version>.tgz
            // Necesitamos: group (scope), name, version, extension="tgz"
            val npmHandler = handlers[ArtifactType.NPM] ?: return@get call.respond(
                HttpStatusCode.InternalServerError,
                "NPM Handler not configured"
            )

            // Usar el handler para extraer coordenadas del nombre del tarball y el contexto del paquete
            // Esto es una simplificación. El handler necesitaría una función para esto.
            // Por ahora, asumimos que el NpmFormatHandler.extractCoordinates puede tomar el tarballFilename
            // y el rawPackageName para resolver las coordenadas.
            // O, más simple, si el tarballFilename es único y el ArtifactService lo encuentra por nombre.
            // Sin embargo, `getArtifact` usa `ArtifactCoordinates`.

            // Parsear nombre y versión del tarball. Ejemplo: "my-package-1.0.0.tgz" o "@scope-my-package-1.0.0.tgz"
            // Esto es complicado porque el nombre del paquete puede tener guiones.
            // El `NpmFormatHandler` debería tener una lógica robusta para esto.
            // Una forma más directa es que el `Artifact` se haya guardado con `filename = tarballFilename`
            // y podamos buscarlo por una combinación de `registryId` y `filename` si `ArtifactRepository` lo permite.
            // O, si las coordenadas (group, name, version) son suficientes:

            val (group, name, version) = parseNpmTarballFilename(
                rawPackageName,
                tarballFilename
            ) // Necesitarías esta función
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Could not parse package coordinates from tarball filename."
                )


            val domainCoordinates = ArtifactCoordinates(
                group = ArtifactGroup(group),
                name = name,
                version = ArtifactVersion(version),
                extension = ArtifactExtension("tgz")
            )

            artifactService.getArtifact(domainCoordinates).fold(
                onSuccess = { artifact ->
                    if (artifact != null) {
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
                                call.respondBytes(
                                    contentBytes,
                                    ContentType.Application.OctetStream
                                ) // application/gzip o similar
                            },
                            onFailure = { ex ->
                                call.application.environment.log.error(
                                    "Failed to retrieve NPM tarball content: ${ex.message}",
                                    ex
                                )
                                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve content.")
                            }
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Tarball '$tarballFilename' not found.")
                    }
                },
                onFailure = { ex ->
                    call.application.environment.log.error("Failed to get NPM tarball artifact: ${ex.message}", ex)
                    call.respond(HttpStatusCode.InternalServerError, "Error finding tarball.")
                }
            )

        } catch (e: IllegalArgumentException) {
            call.application.environment.log.warn("Bad request for NPM tarball GET: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid request parameters: ${e.message}")
        } catch (e: Exception) {
            call.application.environment.log.error("Error processing NPM tarball GET request: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to process tarball download: ${e.message}")
        }
    }


    // POST /-/npm/v1/login - npm login (Placeholder, requiere AuthService)
    route("/-/npm/v1/login") {
        put { // npm login usa PUT
            try {
                val loginRequest = call.receive<NpmLoginRequest>()
                // TODO: Implementar lógica real de autenticación con un AuthService
                // val user = authService.login(loginRequest.name, loginRequest.password)
                // if (user != null) {
                //    val token = authService.generateNpmToken(user) // Generar un token JWT o similar
                //    call.respond(HttpStatusCode.Created, NpmLoginResponse(token = "npm_${token}", ok = true, username=user.username, email=user.email))
                // } else {
                //    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                // }
                call.application.environment.log.info("NPM login attempt for user: ${loginRequest.name}")
                // Placeholder exitoso para desarrollo
                call.respond(
                    HttpStatusCode.Created, // NPM espera 201 en login exitoso
                    NpmLoginResponse(token = "npm_generatedDevToken_${UUID.randomUUID()}", ok = true)
                )
            } catch (e: Exception) {
                call.application.environment.log.error("NPM login error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "login_error", "reason" to e.message))
            }
        }
    }
}

// Función helper (necesita implementación robusta o mover a NpmFormatHandler)
fun parseNpmTarballFilename(rawPackageName: String, tarballFilename: String): Triple<String, String, String> {
    // rawPackageName puede ser "@scope/name" o "name"
    // tarballFilename suele ser "name-version.tgz" o "scope-name-version.tgz" (si el scope se aplana)
    // o a veces solo "name-version.tgz" incluso con scope.
    // Esta es una simplificación extrema.
    val nameNoExt = tarballFilename.removeSuffix(".tgz")

    val (scope, pkgNameInContext) = if (rawPackageName.startsWith("@") && rawPackageName.contains("/")) {
        rawPackageName.substringBeforeLast("/") to rawPackageName.substringAfterLast("/")
    } else {
        null to rawPackageName
    }

    // Intentar encontrar la última parte que es una versión (ej. 1.0.0, 1.2.3-beta.4)
    // Regex para una versión semver simple (no completa)
    val versionRegex =
        Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""")
    var version: String = ""
    var namePart: String = ""

    // Buscar la versión desde el final del nombre del archivo
    var tempName = nameNoExt
    while (tempName.contains("-")) {
        val potentialVersion = tempName.substringAfterLast("-")
        if (versionRegex.matches(potentialVersion)) {
            version = potentialVersion
            namePart = tempName.substringBeforeLast("-")
            break
        }
        tempName = tempName.substringBeforeLast("-")
    }
    if (version == null) { // Si no se encontró con guion, quizás el nombre es solo la versión
        if (versionRegex.matches(nameNoExt) && nameNoExt == pkgNameInContext) { // Poco probable que el nombre del paquete sea solo una versión
            // version = nameNoExt
            // namePart = pkgNameInContext // Esto es ambiguo
            return Triple("", "", "")
        }
        return Triple("", "", "")
    }


    // Validar que namePart coincida con pkgNameInContext (o scope-pkgNameInContext)
    val expectedNamePart = if (scope != null && namePart.startsWith(scope.drop(1) + "-")) { // @scope/pkg -> scope-pkg
        scope.drop(1) + "-" + pkgNameInContext
    } else {
        pkgNameInContext
    }

    return (if (namePart == expectedNamePart) {
        Triple(scope, pkgNameInContext, version)
    } else {
        Triple("", "", "") // No se pudo parsear confiablemente
    }) as Triple<String, String, String>
}