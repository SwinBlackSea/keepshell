package com.keepshell.ssh

import android.graphics.Canvas
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalRenderer
import java.nio.charset.StandardCharsets
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalViewportState(
    val columns: Int,
    val rows: Int,
    val widthPx: Int,
    val heightPx: Int,
    val activeTranscriptRows: Int,
    val scrolledRows: Int,
    val alternateBuffer: Boolean
)

/**
 * Thread-safe xterm screen model shared by the SSH transport and Android view.
 *
 * SSH output is not a stream of immutable lines. Full-screen programs such as
 * Codex repeatedly move the cursor and replace cells in an alternate screen.
 * Termux's mature VT emulator handles that protocol while this wrapper keeps
 * rendering, resizing, output replies and background SSH reads serialized.
 */
class TerminalEngine(
    initialScrollbackLimit: Int = 10_000,
    initialColumns: Int = 80,
    initialRows: Int = 24
) {
    private val lock = Any()
    private var scrollbackLimit = initialScrollbackLimit.coerceIn(1_000, 20_000)
    private var cellWidthPx = 1
    private var cellHeightPx = 1
    private var outboundSink: ((ByteArray) -> Unit)? = null

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            if (count <= 0) return
            outboundSink?.invoke(data.copyOfRange(offset, offset + count))
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = Unit
    }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession?) = Unit
        override fun onTitleChanged(changedSession: TerminalSession?) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession?) = Unit
        override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
        override fun onBell(session: TerminalSession?) = Unit
        override fun onColorsChanged(session: TerminalSession?) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(
            tag: String?,
            message: String?,
            exception: Exception?
        ) = Unit

        override fun logStackTrace(tag: String?, exception: Exception?) = Unit
    }

    private var emulator = createEmulator(
        initialColumns.coerceAtLeast(MIN_COLUMNS),
        initialRows.coerceAtLeast(MIN_ROWS)
    )

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun setOutputSink(sink: ((ByteArray) -> Unit)?) = synchronized(lock) {
        outboundSink = sink
    }

    fun setScrollbackLimit(value: Int) = synchronized(lock) {
        val next = value.coerceIn(1_000, 20_000)
        if (next == scrollbackLimit) return@synchronized

        val preservedText = transcriptTextLocked()
        scrollbackLimit = next
        emulator = createEmulator(emulator.mColumns, emulator.mRows)
        if (preservedText.isNotEmpty()) {
            val bytes = preservedText.toByteArray(StandardCharsets.UTF_8)
            emulator.append(bytes, bytes.size)
        }
        publishLocked()
    }

    fun append(bytes: ByteArray, count: Int = bytes.size) = synchronized(lock) {
        val safeCount = count.coerceIn(0, bytes.size)
        if (safeCount == 0) return@synchronized
        emulator.append(bytes, safeCount)
        publishLocked()
    }

    fun append(text: String) {
        if (text.isEmpty()) return
        append(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun appendSessionDivider(message: String) = synchronized(lock) {
        // A transport may end while a TUI owns the alternate screen. Return to
        // the main buffer before adding local session metadata.
        val text = "\u001B[?1049l\r\n────────── $message ──────────\r\n"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        emulator.append(bytes, bytes.size)
        publishLocked()
    }

    fun clear() = synchronized(lock) {
        emulator = createEmulator(emulator.mColumns, emulator.mRows)
        publishLocked()
    }

    fun resize(
        columns: Int,
        rows: Int,
        newCellWidthPx: Int,
        newCellHeightPx: Int
    ) = synchronized(lock) {
        val safeColumns = columns.coerceAtLeast(MIN_COLUMNS)
        val safeRows = rows.coerceAtLeast(MIN_ROWS)
        cellWidthPx = newCellWidthPx.coerceAtLeast(1)
        cellHeightPx = newCellHeightPx.coerceAtLeast(1)
        if (safeColumns == emulator.mColumns && safeRows == emulator.mRows) {
            return@synchronized
        }
        emulator.resize(safeColumns, safeRows, cellWidthPx, cellHeightPx)
        publishLocked()
    }

    fun render(canvas: Canvas, renderer: TerminalRenderer, topRow: Int) = synchronized(lock) {
        renderer.render(emulator, canvas, topRow, -1, -1, -1, -1)
    }

    fun viewportState(clearScrollCounter: Boolean = false): TerminalViewportState =
        synchronized(lock) {
            val state = TerminalViewportState(
                columns = emulator.mColumns,
                rows = emulator.mRows,
                widthPx = emulator.mColumns * cellWidthPx,
                heightPx = emulator.mRows * cellHeightPx,
                activeTranscriptRows = emulator.screen.activeTranscriptRows,
                scrolledRows = emulator.scrollCounter,
                alternateBuffer = emulator.isAlternateBufferActive
            )
            if (clearScrollCounter) emulator.clearScrollCounter()
            state
        }

    fun visibleText(): String = synchronized(lock) {
        emulator.screen.getSelectedText(
            0,
            0,
            emulator.mColumns,
            emulator.mRows - 1,
            false
        ).trimEnd()
    }

    fun transcriptLines(): List<String> = synchronized(lock) {
        transcriptTextLocked()
            .trimEnd('\n', '\r')
            .split('\n')
            .map { it.trimEnd('\r') }
            .ifEmpty { listOf("") }
    }

    fun keySequence(keyCode: Int, keyModifiers: Int = 0): ByteArray? = synchronized(lock) {
        KeyHandler.getCode(
            keyCode,
            keyModifiers,
            emulator.isCursorKeysApplicationMode,
            emulator.isKeypadApplicationMode
        )?.toByteArray(StandardCharsets.UTF_8)
    }

    fun sendMouseEvent(
        button: Int,
        column: Int,
        row: Int,
        pressed: Boolean
    ) = synchronized(lock) {
        emulator.sendMouseEvent(button, column, row, pressed)
    }

    fun isMouseTrackingActive(): Boolean = synchronized(lock) {
        emulator.isMouseTrackingActive
    }

    fun paste(text: String) = synchronized(lock) {
        emulator.paste(text)
    }

    private fun transcriptTextLocked(): String {
        val firstRow = -emulator.screen.activeTranscriptRows
        return emulator.screen.getSelectedText(
            0,
            firstRow,
            emulator.mColumns,
            emulator.mRows - 1,
            false
        )
    }

    private fun createEmulator(columns: Int, rows: Int): TerminalEmulator {
        TerminalColors.COLOR_SCHEME.updateWith(
            Properties().apply {
                setProperty("foreground", "#d9e2df")
                setProperty("background", "#101516")
                setProperty("cursor", "#68d6ae")
            }
        )
        return TerminalEmulator(
            output,
            columns,
            rows,
            cellWidthPx,
            cellHeightPx,
            (scrollbackLimit + rows).coerceAtMost(50_000),
            client
        )
    }

    private fun publishLocked() {
        _revision.value = _revision.value + 1
    }

    private companion object {
        const val MIN_COLUMNS = 4
        const val MIN_ROWS = 4
    }
}
