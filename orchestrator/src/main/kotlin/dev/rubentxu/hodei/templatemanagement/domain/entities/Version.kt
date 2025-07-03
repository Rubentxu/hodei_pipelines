package dev.rubentxu.hodei.domain.template

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Version(val value: String) {
    companion object {
        fun initial(): Version = Version("v1.0.0")
    }
}