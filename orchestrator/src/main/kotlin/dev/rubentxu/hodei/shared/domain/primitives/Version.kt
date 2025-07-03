package dev.rubentxu.hodei.shared.domain.primitives

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Version(val value: String) {
    init {
        require(value.isNotBlank()) { "Version cannot be blank" }
        require(isValidSemVer(value)) { "Version must follow semantic versioning (x.y.z)" }
    }
    
    private fun isValidSemVer(version: String): Boolean {
        if (version == "latest") return true
        val semVerRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""")
        return semVerRegex.matches(version)
    }
    
    val major: Int
        get() = if (value == "latest") 0 else value.split(".")[0].toInt()
    
    val minor: Int
        get() = if (value == "latest") 0 else value.split(".")[1].toInt()
    
    val patch: Int
        get() = if (value == "latest") 0 else value.split(".")[2].split("-")[0].toInt()
    
    companion object {
        val LATEST = Version("latest")
        
        fun parse(value: String): Version = Version(value)
    }
}