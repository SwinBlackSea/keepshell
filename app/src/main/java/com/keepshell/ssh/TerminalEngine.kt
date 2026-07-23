package com.keepshell.ssh

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small streaming VT state machine for the MVP shell surface.
 *
 * It deliberately parses control bytes instead of stripping ANSI sequences with
 * regular expressions. The implementation preserves incomplete UTF-8 bytes
 * between reads and bounds scrollback in memory.
 */
class TerminalEngine(initialScrollbackLimit: Int = 10_000) {
    private enum class ParserState { TEXT, ESCAPE, CSI }

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var currentLine = StringBuilder()
    private var cursorColumn = 0
    private var parserState = ParserState.TEXT
    private val csi = StringBuilder()
    private var scrollbackLimit = initialScrollbackLimit.coerceAtLeast(1_000)
    private val decoder = Utf8StreamDecoder()

    private val _content = MutableStateFlow<List<String>>(emptyList())
    val content: StateFlow<List<String>> = _content.asStateFlow()

    fun setScrollbackLimit(value: Int) = synchronized(lock) {
        scrollbackLimit = value.coerceIn(1_000, 20_000)
        trimLocked()
        publishLocked()
    }

    fun append(bytes: ByteArray, count: Int = bytes.size) {
        if (count <= 0) return
        val decoded = decoder.decode(bytes, count)
        append(decoded)
    }

    fun append(text: String) = synchronized(lock) {
        text.forEach(::consumeLocked)
        publishLocked()
    }

    fun appendSessionDivider(message: String) = synchronized(lock) {
        commitLineLocked()
        lines.addLast("────────── $message ──────────")
        trimLocked()
        publishLocked()
    }

    fun clear() = synchronized(lock) {
        lines.clear()
        currentLine.clear()
        cursorColumn = 0
        parserState = ParserState.TEXT
        csi.clear()
        publishLocked()
    }

    private fun consumeLocked(char: Char) {
        when (parserState) {
            ParserState.TEXT -> when (char) {
                '\u001B' -> parserState = ParserState.ESCAPE
                '\r' -> cursorColumn = 0
                '\n' -> commitLineLocked()
                '\b' -> if (cursorColumn > 0) cursorColumn--
                '\u0007', '\u0000' -> Unit
                else -> if (!char.isISOControl()) putCharacterLocked(char)
            }

            ParserState.ESCAPE -> {
                if (char == '[') {
                    csi.clear()
                    parserState = ParserState.CSI
                } else {
                    parserState = ParserState.TEXT
                }
            }

            ParserState.CSI -> {
                if (char in '@'..'~') {
                    executeCsiLocked(char, csi.toString())
                    csi.clear()
                    parserState = ParserState.TEXT
                } else if (csi.length < 32) {
                    csi.append(char)
                } else {
                    csi.clear()
                    parserState = ParserState.TEXT
                }
            }
        }
    }

    private fun putCharacterLocked(char: Char) {
        while (currentLine.length < cursorColumn) currentLine.append(' ')
        if (cursorColumn < currentLine.length) {
            currentLine.setCharAt(cursorColumn, char)
        } else {
            currentLine.append(char)
        }
        cursorColumn++
    }

    private fun executeCsiLocked(command: Char, rawParameters: String) {
        val parameters = rawParameters
            .trimStart('?', '>')
            .split(';')
            .mapNotNull { it.toIntOrNull() }
        val amount = (parameters.firstOrNull() ?: 1).coerceAtLeast(1)

        when (command) {
            'C' -> cursorColumn = (cursorColumn + amount).coerceAtMost(currentLine.length)
            'D' -> cursorColumn = (cursorColumn - amount).coerceAtLeast(0)
            'G' -> cursorColumn = (amount - 1).coerceAtLeast(0)
            'K' -> when (parameters.firstOrNull() ?: 0) {
                0 -> if (cursorColumn < currentLine.length) {
                    currentLine.delete(cursorColumn, currentLine.length)
                }
                1 -> {
                    val end = cursorColumn.coerceAtMost(currentLine.length)
                    repeat(end) { currentLine.setCharAt(it, ' ') }
                }
                2 -> {
                    currentLine.clear()
                    cursorColumn = 0
                }
            }
            'J' -> if ((parameters.firstOrNull() ?: 0) == 2) {
                lines.clear()
                currentLine.clear()
                cursorColumn = 0
            }
            'H', 'f' -> {
                // The line-oriented MVP renderer treats cursor-home as a fresh line.
                if (currentLine.isNotEmpty()) commitLineLocked()
                cursorColumn = 0
            }
            'm', 'A', 'B', 'h', 'l' -> Unit
        }
    }

    private fun commitLineLocked() {
        lines.addLast(currentLine.toString())
        currentLine = StringBuilder()
        cursorColumn = 0
        trimLocked()
    }

    private fun trimLocked() {
        while (lines.size > scrollbackLimit) lines.removeFirst()
    }

    private fun publishLocked() {
        _content.value = buildList(lines.size + 1) {
            addAll(lines)
            if (currentLine.isNotEmpty() || lines.isEmpty()) add(currentLine.toString())
        }
    }
}

private class Utf8StreamDecoder {
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    @Synchronized
    fun decode(input: ByteArray, count: Int): String {
        val safeCount = count.coerceIn(0, input.size)
        val merged = ByteArray(pending.size + safeCount)
        pending.copyInto(merged)
        input.copyInto(merged, destinationOffset = pending.size, endIndex = safeCount)

        val byteBuffer = ByteBuffer.wrap(merged)
        val charBuffer = CharBuffer.allocate(merged.size.coerceAtLeast(1))
        decoder.decode(byteBuffer, charBuffer, false)
        pending = ByteArray(byteBuffer.remaining())
        byteBuffer.get(pending)
        charBuffer.flip()
        return charBuffer.toString()
    }
}
