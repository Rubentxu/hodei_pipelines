package dev.rubentxu.hodei.pipelines.dsl.model

/**
 * Tipos de stages disponibles en el Pipeline DSL.
 */
enum class StageType {
    BUILD,
    TEST,
    DEPLOY,
    CUSTOM
}