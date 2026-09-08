package com.s17labs.koda.model

import org.junit.Assert.*
import org.junit.Test

class OpenFileTest {

    @Test
    fun roundTrip_plainContent() {
        val file = OpenFile(
            name = "notes.txt",
            path = "content://doc/1",
            content = "hello\nworld",
            originalContent = "hello\nworld",
            isNew = false
        )
        val restored = OpenFile.fromJson(file.toJson())
        assertNotNull(restored)
        assertEquals("notes.txt", restored!!.name)
        assertEquals("content://doc/1", restored.path)
        assertEquals("hello\nworld", restored.content)
        assertEquals("hello\nworld", restored.originalContent)
        assertEquals(false, restored.isNew)
    }

    @Test
    fun roundTrip_contentWithPipes_keepsFullContent() {
        val content = "# Palette\n\n| Token | Hex | Role |\n| Ink | #0F1A2E | dark |"
        val restored = OpenFile.fromJson(
            OpenFile(name = "palette.md", path = "content://doc/2", content = content).toJson()
        )
        assertNotNull(restored)
        assertEquals(content, restored!!.content)
        assertEquals(content, restored.originalContent)
    }

    @Test
    fun roundTrip_backslashesAndPipes() {
        val content = "back\\slash|pipe\\\\end"
        val restored = OpenFile.fromJson(
            OpenFile(name = "a|b.txt", path = "content://doc/3", content = content).toJson()
        )
        assertNotNull(restored)
        assertEquals("a|b.txt", restored!!.name)
        assertEquals(content, restored.content)
    }

    @Test
    fun roundTrip_newFileWithoutPath() {
        val restored = OpenFile.fromJson(
            OpenFile(name = "newfile.txt", content = "x|y").toJson()
        )
        assertNotNull(restored)
        assertNull(restored!!.path)
        assertTrue(restored.isNew)
        assertEquals("x|y", restored.content)
    }

    @Test
    fun legacy_unescapedSessionData_restoresInFull() {
        // Format written before escaping existed: raw pipes in content.
        val legacy = "palette.md|content://doc/2|# Palette\n\n| Token | Hex |"
        val restored = OpenFile.fromJson(legacy)
        assertNotNull(restored)
        assertEquals("palette.md", restored!!.name)
        assertEquals("content://doc/2", restored.path)
        assertEquals("# Palette\n\n| Token | Hex |", restored.content)
    }

    @Test
    fun malformedData_returnsNull() {
        assertNull(OpenFile.fromJson("no-delimiters"))
        assertNull(OpenFile.fromJson("only|one"))
    }
}
