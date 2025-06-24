package dev.rubentxu.hodei.pipelines.steps.dsl

import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionStep

/**
 * Common helper functions for DSL extensions.
 */

/**
 * Helper function para agregar extension steps al StepsBuilder.
 */
fun StepsBuilder.addExtensionStep(step: ExtensionStep) {
    // Usar reflexión para agregar el step a la lista interna
    val stepsField = this::class.java.getDeclaredField("steps")
    stepsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val steps = stepsField.get(this) as MutableList<dev.rubentxu.hodei.pipelines.dsl.model.Step>
    steps.add(dev.rubentxu.hodei.pipelines.dsl.model.Step.Custom(
        action = step.action,
        parameters = step.parameters.mapValues { it.value.toString() },
        name = step.name,
        continueOnError = step.continueOnError,
        timeout = step.timeout
    ))
}