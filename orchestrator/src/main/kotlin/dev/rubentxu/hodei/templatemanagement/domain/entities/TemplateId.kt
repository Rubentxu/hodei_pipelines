package dev.rubentxu.hodei.templatemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TemplateId(val value: String) {
    companion object {
        fun generate(): TemplateId = TemplateId(DomainId.generate().value)
    }
}