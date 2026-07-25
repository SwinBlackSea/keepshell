package com.keepshell.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEngineTest {
    @Test
    fun `preserves utf8 characters split across network reads`() {
        val engine = TerminalEngine()
        val bytes = "状态正常".toByteArray(Charsets.UTF_8)

        engine.append(bytes.copyOfRange(0, 2))
        engine.append(bytes.copyOfRange(2, 7))
        engine.append(bytes.copyOfRange(7, bytes.size))

        assertEquals(listOf("状态正常"), engine.transcriptLines())
    }

    @Test
    fun `carriage return updates a progress line without adding rows`() {
        val engine = TerminalEngine()

        engine.append("progress 10%\rprogress 90%")

        assertEquals(listOf("progress 90%"), engine.transcriptLines())
    }

    @Test
    fun `large streaming output remains ordered and bounded`() {
        val engine = TerminalEngine(initialScrollbackLimit = 1_000, initialRows = 4)

        repeat(1_100) { index ->
            engine.append("line-$index\r\n")
        }

        val content = engine.transcriptLines()
        // The configured limit is transcript history; the visible PTY rows are
        // retained in addition to that history.
        assertEquals(1_003, content.size)
        assertEquals("line-97", content.first())
        assertEquals("line-1099", content.last())
        assertTrue(content.zipWithNext().all { (first, second) ->
            second.substringAfter('-').toInt() == first.substringAfter('-').toInt() + 1
        })
    }

    @Test
    fun `osc window title terminated by bell is not rendered`() {
        val engine = TerminalEngine()

        engine.append("\u001B]0;ubuntu@server: ~\u0007ubuntu@server:~$ ")

        assertEquals(listOf("ubuntu@server:~$"), engine.transcriptLines())
    }

    @Test
    fun `chunked osc window title terminated by st is not rendered`() {
        val engine = TerminalEngine()

        engine.append("\u001B]2;remote title\u001B")
        engine.append("\\ready")

        assertEquals(listOf("ready"), engine.transcriptLines())
    }

    @Test
    fun `cursor addressing replaces screen cells instead of creating narrow lines`() {
        val engine = TerminalEngine(initialColumns = 40, initialRows = 8)

        engine.append("Work 2 killing Wrong Work")
        engine.append("\u001B[1;1HReady")
        engine.append("\u001B[3;1H› Improve documentation")

        val visible = engine.visibleText().lines()
        assertTrue(visible.first().startsWith("Ready"))
        assertEquals("› Improve documentation", visible[2])
    }

    @Test
    fun `alternate screen keeps full screen tui rows`() {
        val engine = TerminalEngine(initialColumns = 40, initialRows = 8)

        engine.append("\u001B[?1049h\u001B[2J\u001B[H")
        engine.append("OpenAI Codex")
        engine.append("\u001B[4;1H› Ask Codex anything")

        val state = engine.viewportState()
        val visible = engine.visibleText().lines()
        assertTrue(state.alternateBuffer)
        assertEquals("OpenAI Codex", visible[0])
        assertEquals("› Ask Codex anything", visible[3])
    }
}
