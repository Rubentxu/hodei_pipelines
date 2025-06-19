package dev.rubentxu.hodei.packages.app.features.packages.routes

// Importa el ArtifactServicePort y los modelos necesarios del dominio/aplicación
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.command.UploadArtifactCommand
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactClassifier
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactExtension
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactGroup
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactType
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactVersion
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.ports.FormatHandler
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.service.ArtifactServicePort
import dev.rubentxu.hodei.packages.artifacts.identityaccess.model.UserId
import dev.rubentxu.hodei.packages.artifacts.registrymanagement.model.RegistryId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import dev.rubentxu.hodei.packages.artifacts.artifactmanagement.model.ArtifactCoordinates as DomainArtifactCoordinates

// Asumimos que 'handlers' se inyecta o está disponible en este scope
fun Route.mavenRoutes(
    artifactService: ArtifactServicePort,
    handlers: Map<ArtifactType, FormatHandler> // Necesario para generar maven-metadata.xml
) {

    authenticate("basicAuth") {
        // PUT /maven/{repoId}/{groupId}/{artifactId}/{version}/{file}
        // El path de Maven es más bien /{repoId}/{groupId.replace('.','/')}/{artifactId}/{version}/{file}
        // Por simplicidad, mantenemos tu estructura de path, pero Maven convierte '.' en '/' para groupId.
        put("/maven/{repoId}/{groupId...}/{artifactId}/{version}/{file}") {
            val repoIdStr = call.parameters["repoId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                "Repository ID is missing"
            )
            // groupId puede contener '/', así que usamos '...' para capturar el path completo
            val groupIdPathParts = call.parameters.getAll("groupId") ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                "GroupId is missing or invalid"
            )
            val groupId = groupIdPathParts.joinToString(".") // Reconstruir groupId

            val artifactIdFromPath = call.parameters["artifactId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                "ArtifactId is missing"
            )
            val versionFromPath =
                call.parameters["version"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Version is missing")
            val fileName =
                call.parameters["file"] ?: return@put call.respond(HttpStatusCode.BadRequest, "File name is missing")

            val principal = call.principal<UserIdPrincipal>()
            if (principal == null) {
                call.application.environment.log.warn("Principal not found in authenticated Maven PUT route.")
                return@put call.respond(HttpStatusCode.Unauthorized, "Authentication failed: User principal not found.")
            }
            val uploaderUserId = UserId(principal.name) // Renombrado para coincidir con UploadArtifactCommand

            try {
                val registryId = RegistryId(UUID.fromString(repoIdStr)) // Asumimos que repoIdStr es un UUID válido o tu RegistryId
                val fileContent = call.receiveStream().readBytes()

                // Construir el comando para ArtifactService
                val command = UploadArtifactCommand(
                    registryId = registryId,
                    filename = fileName,
                    content = fileContent,
                    artifactType = ArtifactType.MAVEN,
                    providedUserMetadata = emptyMap(), // O extraer de headers si es necesario
                    uploaderUserId = uploaderUserId
                )

                val result = artifactService.uploadArtifact(command)

                result.fold(
                    onSuccess = { artifact ->
                        // Maven espera 201 para subidas exitosas de artefactos principales
                        // y a veces 200 o 202 para metadatos (.pom, .sha1, etc.)
                        // Si es un .pom, .sha1, etc., el cliente Maven podría no esperar un cuerpo.
                        // Para el artefacto principal, 201 es bueno.
                        call.respond(HttpStatusCode.Created, "Artifact ${artifact.coordinates} published successfully.")
                    },
                    onFailure = { exception ->
                        call.application.environment.log.error(
                            "Failed to publish Maven artifact: ${exception.message}",
                            exception
                        )
                        val statusCode = when (exception) {
                            is IllegalArgumentException -> HttpStatusCode.BadRequest
                            is IllegalStateException -> HttpStatusCode.Conflict // Ej: Artifact already exists
                            is UnsupportedOperationException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(statusCode, "Failed to publish artifact: ${exception.message}")
                    }
                )

            } catch (e: IllegalArgumentException) { // Para RegistryId(repoIdStr) si falla
                call.application.environment.log.warn("Bad request for Maven PUT: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, "Invalid request parameters: ${e.message}")
            } catch (e: Exception) {
                call.application.environment.log.error("Error processing Maven artifact PUT request: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to process artifact upload: ${e.message}")
            }
        }
    }

    // GET /maven/{repoId}/{groupId...}/{artifactId}/maven-metadata.xml
    get("/maven/{repoId}/{groupId...}/{artifactId}/maven-metadata.xml") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing")
        val groupIdPathParts = call.parameters.getAll("groupId") ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "GroupId is missing"
        )
        val groupId = groupIdPathParts.joinToString(".")
        val artifactId =
            call.parameters["artifactId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "ArtifactId is missing")

        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))
            // Asumimos que ArtifactServicePort.getAllVersions ahora toma registryId
            val result = artifactService.getAllVersions(groupId, artifactId)

            result.fold(
                onSuccess = { artifactsInRepo ->
                    if (artifactsInRepo.isEmpty()) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            "No artifacts found for $groupId:$artifactId in repo $repoIdStr"
                        )
                        return@fold
                    }

                    val mavenHandler = handlers[ArtifactType.MAVEN]
                    if (mavenHandler == null) {
                        call.application.environment.log.error("MavenFormatHandler not found.")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Server configuration error: Maven handler missing."
                        )
                        return@fold
                    }

                    // Construir la URL base del repositorio para que el handler la use
                    // Esto es un ejemplo, ajusta según cómo construyes tus URLs
                    val scheme = call.request.origin.scheme
                    val host = call.request.origin.serverHost
                    val port = call.request.origin.serverPort.let { if (it == 80 || it == 443) "" else ":$it" }
                    val registryBaseUrl = "$scheme://$host$port/maven/$repoIdStr"


                    mavenHandler.generateRepositoryMetadata(groupId, artifactId, artifactsInRepo, registryBaseUrl).fold(
                        onSuccess = { metadataXml ->
                            call.respondText(metadataXml, ContentType.Application.Xml)
                        },
                        onFailure = { ex ->
                            call.application.environment.log.error(
                                "Failed to generate maven-metadata.xml: ${ex.message}",
                                ex
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                "Failed to generate metadata: ${ex.message}"
                            )
                        }
                    )
                },
                onFailure = { exception ->
                    call.application.environment.log.error(
                        "Failed to retrieve versions for $groupId:$artifactId in repo $repoIdStr: ${exception.message}",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve artifact versions: ${exception.message}"
                    )
                }
            )
        } catch (e: IllegalArgumentException) { // Para RegistryId(repoIdStr)
            call.application.environment.log.warn("Bad request for maven-metadata.xml: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid repository ID: ${e.message}")
        } catch (e: Exception) {
            call.application.environment.log.error("Error processing maven-metadata.xml request: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to process metadata request: ${e.message}")
        }
    }

    // GET /maven/{repoId}/{groupId...}/{artifactId}/{version}/{file}
    get("/maven/{repoId}/{groupId...}/{artifactId}/{version}/{file}") {
        val repoIdStr =
            call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Repository ID is missing")
        val groupIdPathParts = call.parameters.getAll("groupId") ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "GroupId is missing"
        )
        val groupId = groupIdPathParts.joinToString(".")
        val artifactIdFromPath =
            call.parameters["artifactId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "ArtifactId is missing")
        val versionFromPath =
            call.parameters["version"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Version is missing")
        val fileName =
            call.parameters["file"] ?: return@get call.respond(HttpStatusCode.BadRequest, "File name is missing")

        try {
            val registryId = RegistryId(UUID.fromString(repoIdStr))

            // Parsear classifier y extension del fileName
            // Esta lógica es específica de Maven y está bien aquí o en un helper.
            val extension: String
            val classifier: String
            val expectedPrefix = "$artifactIdFromPath-$versionFromPath"

            when {
                fileName == "$expectedPrefix.pom" -> {
                    extension = "pom"
                    classifier = ""
                }

                fileName.startsWith(expectedPrefix) && (fileName.endsWith(".jar") || fileName.endsWith(".war") || fileName.endsWith(
                    ".ear"
                ) || fileName.endsWith(".zip")) -> {
                    val remaining = fileName.substringAfter(expectedPrefix) // Puede ser "-classifier.ext" o ".ext"
                    if (remaining.startsWith("-")) { // Tiene clasificador
                        classifier = remaining.substringBeforeLast('.').drop(1) // Quita el '-' inicial
                        extension = remaining.substringAfterLast('.')
                    } else { // No tiene clasificador
                        classifier = ""
                        extension = remaining.drop(1) // Quita el '.' inicial
                    }
                }
                // Para archivos de checksums u otros metadatos
                fileName.endsWith(".sha1") || fileName.endsWith(".md5") || fileName.endsWith(".sha256") || fileName.endsWith(
                    ".sha512"
                ) || fileName.endsWith(".asc") -> {
                    // Estos son metadatos del artefacto principal o del POM.
                    // El `ArtifactService` no los maneja como artefactos separados con su propio contenido si son checksums.
                    // Si son firmas PGP (.asc), podrían ser artefactos separados.
                    // Por ahora, si es un checksum, el cliente Maven espera que el servidor lo genere o lo devuelva si se subió.
                    // Esta implementación asume que el cliente sube estos archivos y se almacenan como cualquier otro.
                    // Si se deben generar dinámicamente, se necesitaría lógica adicional.
                    val baseFileName = fileName.substringBeforeLast('.') // e.g., my-artifact-1.0.jar
                    val checksumOrSignatureType = fileName.substringAfterLast('.')

                    val mainArtifactExtension = baseFileName.substringAfterLast('.', "")
                    val mainArtifactNameWithoutExt = baseFileName.substringBeforeLast('.')
                    val mainArtifactExpectedPrefix = "$artifactIdFromPath-$versionFromPath"
                    val mainArtifactClassifier: String

                    when {
                        mainArtifactNameWithoutExt == mainArtifactExpectedPrefix -> mainArtifactClassifier = ""
                        mainArtifactNameWithoutExt.startsWith("$mainArtifactExpectedPrefix-") -> {
                            mainArtifactClassifier =
                                mainArtifactNameWithoutExt.substringAfter("$mainArtifactExpectedPrefix-")
                        }

                        else -> {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                "Invalid base file name for checksum/signature: $fileName"
                            )
                        }
                    }

                    val mainCoordinates = DomainArtifactCoordinates(
                        group = ArtifactGroup(groupId),
                        name = artifactIdFromPath,
                        version = ArtifactVersion(versionFromPath),
                        classifier = ArtifactClassifier(mainArtifactClassifier), // El clasificador del POM es el mismo que el del artificiomainArtifactClassifier,
                        extension = ArtifactExtension(mainArtifactExtension), // El extension del POM es el mismo que el del mainArtifactExtension
                    )
                    val artifactResult = artifactService.getArtifact(mainCoordinates,)
                    // ... Lógica para generar/devolver el checksum/firma ...
                    // Esto es complejo. Por ahora, asumamos que se suben como archivos normales.
                    // Si el archivo es "mi.jar.sha1", trataremos "mi.jar.sha1" como el nombre del artefacto.
                    // El `MavenFormatHandler` podría tener que ser más inteligente para esto.
                    // O, más simple: si el cliente pide `foo.jar.sha1`, y `foo.jar.sha1` fue subido, se sirve.
                    // Si no, se podría intentar generar el SHA1 de `foo.jar`.
                    // Por ahora, tratamos todos los archivos como artefactos discretos.
                    extension =
                        fileName.substringAfter("$artifactIdFromPath-$versionFromPath").substringAfterLast('.', "")
                    classifier =
                        fileName.substringAfter("$artifactIdFromPath-$versionFromPath").substringBeforeLast('.').let {
                            if (it.startsWith("-")) it.drop(1) else ""
                        }

                }

                else -> {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "File name '$fileName' does not match expected Maven format or unsupported metadata file."
                    )
                }
            }
            if (extension.isEmpty()) {
                return@get call.respond(HttpStatusCode.BadRequest, "File name missing or invalid extension: $fileName")
            }


            val domainCoordinates = DomainArtifactCoordinates(
                group = ArtifactGroup(groupId),
                name = artifactIdFromPath,
                version = ArtifactVersion(versionFromPath),
                classifier = ArtifactClassifier(classifier), // Usar el clasificador parseado, o ArtifactClassifier.NONE si no hay classifier,
                extension = ArtifactExtension(extension), // Usar la extensión parseada, o ArtifactExtension.NONE si no hay extension // Usar la extensión parseada
            )

            // Asumimos que ArtifactServicePort.getArtifact ahora toma registryId
            val artifactResult = artifactService.getArtifact(domainCoordinates)

            artifactResult.fold(
                onSuccess = { artifact ->
                    if (artifact != null) {
                        // El artefacto existe con estas coordenadas en este repositorio.
                        // Ahora recuperamos su contenido.
                        val contentResult = artifactService.retrieveArtifactContent(registryId, artifact.contentHash)
                        contentResult.fold(
                            onSuccess = { contentBytes ->
                                // Registrar el evento de descarga
                                val principal =
                                    call.principal<UserIdPrincipal>() // Puede ser null si la ruta no está autenticada
                                val downloadedBy = principal?.let { UserId(it.name) }
                                artifactService.downloadArtifact(
                                    artifactId = artifact.id,
                                    downloadedBy = downloadedBy,
                                    clientIp = call.request.origin.remoteHost,
                                    userAgent = call.request.headers[HttpHeaders.UserAgent]
                                ) // Ignoramos el resultado del evento por ahora

                                call.respondBytes(contentBytes, ContentType.defaultForFileExtension(extension))
                            },
                            onFailure = { ex ->
                                call.application.environment.log.error(
                                    "Failed to retrieve content for $domainCoordinates in repo $repoIdStr: ${ex.message}",
                                    ex
                                )
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    "Failed to retrieve artifact content."
                                )
                            }
                        )
                    } else {
                        // Si el archivo es un .pom y no se encuentra como artefacto, Maven a veces espera que se genere.
                        if (extension == "pom") {
                            // Intentar generar el POM dinámicamente si no existe como artefacto almacenado.
                            // Esto requeriría que `generateArtifactDescriptor` pueda funcionar solo con coordenadas
                            // o que busquemos el JAR principal y generemos el POM a partir de él.
                            // Por ahora, si no se encuentra, es un 404.
                            // TODO: Considerar la generación dinámica de POMs si no se subieron explícitamente.
                        }
                        call.respond(
                            HttpStatusCode.NotFound,
                            "Artifact $fileName not found in repository $repoIdStr with specified coordinates."
                        )
                    }
                },
                onFailure = { exception ->
                    call.application.environment.log.error(
                        "Failed to get artifact metadata for $domainCoordinates: ${exception.message}",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve artifact metadata."
                    )
                }
            )

        } catch (e: IllegalArgumentException) {
            call.application.environment.log.warn("Bad request for Maven GET: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid request parameters: ${e.message}")
        } catch (e: Exception) {
            call.application.environment.log.error(
                "Error processing Maven artifact GET request for $fileName: ${e.message}",
                e
            )
            call.respond(HttpStatusCode.InternalServerError, "Failed to process artifact download: ${e.message}")
        }
    }
}