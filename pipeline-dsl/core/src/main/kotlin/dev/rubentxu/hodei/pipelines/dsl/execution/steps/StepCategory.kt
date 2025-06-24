package dev.rubentxu.hodei.pipelines.dsl.execution.steps

/**
 * Categorías de steps para mejor organización y extensibilidad.
 */
enum class StepCategory {
    /** Steps básicos de ejecución (sh, bat, echo) */
    BASIC,
    
    /** Steps de control de flujo (dir, withEnv) */
    FLOW_CONTROL,
    
    /** Steps de gestión de código fuente (checkout, git) */
    SCM,
    
    /** Steps de construcción y empaquetado (docker, gradle, maven) */
    BUILD,
    
    /** Steps de testing y calidad (test, sonar) */
    TESTING,
    
    /** Steps de artefactos y archivado (archive, publish) */
    ARTIFACTS,
    
    /** Steps de notificación y comunicación (email, slack) */
    NOTIFICATION,
    
    /** Steps de despliegue (deploy, k8s) */
    DEPLOYMENT,
    
    /** Steps de seguridad (scan, sign) */
    SECURITY,
    
    /** Steps personalizados o de extensión */
    CUSTOM
}

/**
 * Registro de categorías de steps.
 */
interface StepCategoryRegistry {
    fun getCategory(stepType: String): StepCategory
    fun getStepsInCategory(category: StepCategory): List<String>
    fun registerStep(stepType: String, category: StepCategory)
}