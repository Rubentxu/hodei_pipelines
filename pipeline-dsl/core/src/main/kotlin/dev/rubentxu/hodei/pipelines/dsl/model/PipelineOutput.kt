package dev.rubentxu.hodei.pipelines.dsl.model

import kotlinx.serialization.Serializable

/**
 * Chunk de output coherente del Pipeline DSL (equivalente a JobOutputChunk).
 */
@Serializable
data class PipelineOutputChunk(
    val data: ByteArray,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PipelineOutputChunk

        if (!data.contentEquals(other.data)) return false
        if (isError != other.isError) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isError.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}