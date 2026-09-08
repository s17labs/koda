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
        return "${escape(name)}|${escape(path ?: "")}|${escape(content)}"
    }

    companion object {
        fun fromJson(data: String): OpenFile? {
            val parts = splitEscaped(data)
            if (parts.size < 3) return null
            // Sessions saved before escaping existed contain raw pipes:
            // rejoin everything after the path so they restore in full too.
            val path = unescape(parts[1])
            val content = unescape(parts.subList(2, parts.size).joinToString("|"))
            return OpenFile(
                name = unescape(parts[0]),
                path = path.ifEmpty { null },
                content = content,
                originalContent = content,
                isNew = path.isEmpty()
            )
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("|", "\\|")

        private fun unescape(value: String): String {
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    val next = value[i + 1]
                    if (next == '|' || next == '\\') {
                        out.append(next)
                        i += 2
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        private fun splitEscaped(value: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length && value[i + 1] == '|') {
                    current.append("\\|")
                    i += 2
                    continue
                }
                if (c == '|') {
                    parts.add(current.toString())
                    current.clear()
                    i++
                    continue
                }
                current.append(c)
                i++
            }
            parts.add(current.toString())
            return parts
        }
    }
}
