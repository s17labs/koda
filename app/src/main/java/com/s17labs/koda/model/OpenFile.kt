package com.s17labs.koda.model

data class OpenFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "newfile.txt",
    var path: String? = null,
    var content: String = "",
    var originalContent: String = "",
    var isModified: Boolean = false,
    var isNew: Boolean = true
) {
    fun toJson(): String {
        return "$name|$path|$content"
    }

    companion object {
        fun fromJson(data: String): OpenFile? {
            val parts = data.split("|")
            if (parts.size < 3) return null
            return OpenFile(
                name = parts[0],
                path = parts[1].ifEmpty { null },
                content = parts[2],
                originalContent = parts[2],
                isNew = parts[1].isEmpty()
            )
        }
    }
}
