package dev.rubentxu.hodei.pipelines.dsl

/**
 * DSL Marker para el Pipeline DSL que previene el uso accidental de
 * elementos DSL desde scopes incorrectos y proporciona mejor IDE support.
 * 
 * Este marker asegura type safety y previene la construcción de pipelines inválidos.
 * 
 * @since 1.0.0
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class PipelineDslMarker