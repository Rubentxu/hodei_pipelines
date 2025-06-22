package dev.rubentxu.hodei.pipelines.domain.orchestration

import java.time.Duration
import java.time.Instant

/**
 * Scaling Policy ID
 */
@JvmInline
value class ScalingPolicyId(val value: String) {
    init {
        require(value.isNotBlank()) { "ScalingPolicyId cannot be blank" }
    }
}

/**
 * Scaling Policy - Defines how and when to scale workers
 */
data class ScalingPolicy(
    val id: ScalingPolicyId,
    val name: String,
    val minWorkers: Int = 0,
    val maxWorkers: Int = 10,
    val scaleUpThreshold: ScaleThreshold,
    val scaleDownThreshold: ScaleThreshold,
    val cooldownPeriod: Duration = Duration.ofMinutes(2),
    val scaleUpCooldown: Duration = Duration.ofMinutes(1),  // Faster scale up
    val scaleDownCooldown: Duration = Duration.ofMinutes(5), // Slower scale down
    val strategy: ScalingStrategy = ScalingStrategy.REACTIVE,
    val enabled: Boolean = true,
    val lastScaleAction: ScaleAction? = null
) {
    
    /**
     * Check if scaling up is needed
     */
    fun shouldScaleUp(queueLength: Int, avgWaitTime: Duration, availableWorkers: Int): Boolean {
        if (!enabled || availableWorkers >= maxWorkers) return false
        if (isInCooldownPeriod(ScaleDirection.UP)) return false
        
        return scaleUpThreshold.isMet(queueLength, avgWaitTime, availableWorkers)
    }
    
    /**
     * Check if scaling down is needed
     */
    fun shouldScaleDown(queueLength: Int, avgWaitTime: Duration, availableWorkers: Int): Boolean {
        if (!enabled || availableWorkers <= minWorkers) return false
        if (isInCooldownPeriod(ScaleDirection.DOWN)) return false
        
        return scaleDownThreshold.isMet(queueLength, avgWaitTime, availableWorkers)
    }
    
    /**
     * Calculate optimal number of workers needed
     */
    fun calculateOptimalWorkers(
        queueLength: Int,
        avgWaitTime: Duration,
        currentWorkers: Int,
        resourceAvailability: ResourceAvailability
    ): Int {
        val baseNeeded = when (strategy) {
            ScalingStrategy.REACTIVE -> calculateReactiveWorkers(queueLength, avgWaitTime, currentWorkers)
            ScalingStrategy.PREDICTIVE -> calculatePredictiveWorkers(queueLength, avgWaitTime, currentWorkers)
            ScalingStrategy.RESOURCE_BASED -> calculateResourceBasedWorkers(queueLength, resourceAvailability, currentWorkers)
        }
        
        return baseNeeded.coerceIn(minWorkers, maxWorkers)
    }
    
    private fun calculateReactiveWorkers(queueLength: Int, avgWaitTime: Duration, currentWorkers: Int): Int {
        return when {
            queueLength == 0 -> minWorkers
            queueLength <= 2 -> currentWorkers
            avgWaitTime > Duration.ofMinutes(2) -> currentWorkers + 2
            avgWaitTime > Duration.ofSeconds(30) -> currentWorkers + 1
            else -> currentWorkers
        }
    }
    
    private fun calculatePredictiveWorkers(queueLength: Int, avgWaitTime: Duration, currentWorkers: Int): Int {
        // Simple predictive algorithm - can be enhanced with ML
        val demandFactor = (queueLength * 0.5 + avgWaitTime.seconds * 0.1).toInt()
        return (currentWorkers + demandFactor).coerceAtLeast(minWorkers)
    }
    
    private fun calculateResourceBasedWorkers(
        queueLength: Int, 
        resourceAvailability: ResourceAvailability, 
        currentWorkers: Int
    ): Int {
        val maxPossibleWorkers = resourceAvailability.getMaxPossibleWorkers()
        val demandBasedWorkers = (queueLength * 1.2).toInt() // Buffer factor
        
        return minOf(demandBasedWorkers, maxPossibleWorkers, maxWorkers)
    }
    
    private fun isInCooldownPeriod(direction: ScaleDirection): Boolean {
        val lastAction = lastScaleAction ?: return false
        val cooldown = when (direction) {
            ScaleDirection.UP -> scaleUpCooldown
            ScaleDirection.DOWN -> scaleDownCooldown
        }
        
        return lastAction.timestamp.plus(cooldown).isAfter(Instant.now())
    }
    
    /**
     * Record a scaling action
     */
    fun recordScaleAction(direction: ScaleDirection, fromSize: Int, toSize: Int): ScalingPolicy {
        return copy(
            lastScaleAction = ScaleAction(
                direction = direction,
                fromSize = fromSize,
                toSize = toSize,
                timestamp = Instant.now()
            )
        )
    }
}

/**
 * Scale Threshold - Conditions for triggering scaling
 */
data class ScaleThreshold(
    val queueLength: Int? = null,
    val avgWaitTime: Duration? = null,
    val workerUtilization: Double? = null,        // 0.0 - 1.0
    val pendingJobPriority: JobPriority? = null,  // Scale up for high priority jobs
    val resourceUtilization: Double? = null       // 0.0 - 1.0
) {
    
    fun isMet(queueLength: Int, avgWaitTime: Duration, availableWorkers: Int): Boolean {
        val queueMet = this.queueLength?.let { queueLength >= it } ?: true
        val waitTimeMet = this.avgWaitTime?.let { avgWaitTime >= it } ?: true
        val utilizationMet = this.workerUtilization?.let { 
            // Simple utilization check - can be enhanced
            availableWorkers == 0 || (availableWorkers.toDouble() / (availableWorkers + queueLength)) <= it 
        } ?: true
        
        return queueMet && waitTimeMet && utilizationMet
    }
}

/**
 * Scaling Strategy
 */
enum class ScalingStrategy {
    REACTIVE,      // React to current demand
    PREDICTIVE,    // Predict future demand
    RESOURCE_BASED // Scale based on available resources
}

/**
 * Scale Direction
 */
enum class ScaleDirection {
    UP, DOWN
}

/**
 * Scale Action Record
 */
data class ScaleAction(
    val direction: ScaleDirection,
    val fromSize: Int,
    val toSize: Int,
    val timestamp: Instant = Instant.now(),
    val reason: String? = null
)

