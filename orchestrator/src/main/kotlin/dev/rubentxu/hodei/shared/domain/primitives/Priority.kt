package dev.rubentxu.hodei.shared.domain.primitives

import kotlinx.serialization.Serializable

@Serializable
enum class Priority(val value: Int) {
    LOW(1),
    NORMAL(5),
    MEDIUM(5),
    HIGH(10),
    CRITICAL(20);
    
    companion object {
        fun fromValue(value: Int): Priority = 
            values().find { it.value == value } ?: NORMAL
    }
}