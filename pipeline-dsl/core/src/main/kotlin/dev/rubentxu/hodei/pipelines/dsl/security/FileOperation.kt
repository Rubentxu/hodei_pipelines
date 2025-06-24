package dev.rubentxu.hodei.pipelines.dsl.security

import kotlinx.serialization.Serializable

/**
 * Tipos de operaciones de archivo para el control de seguridad.
 */
@Serializable
enum class FileOperation {
    READ,
    WRITE,
    DELETE,
    EXECUTE,
    CREATE,
    MODIFY
}