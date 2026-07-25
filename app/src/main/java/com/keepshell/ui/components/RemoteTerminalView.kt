package com.keepshell.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.keepshell.ssh.TerminalEngine
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android View that renders a remote SSH PTY and forwards IME/hardware input
 * directly to that PTY. It intentionally has no local prompt or edit buffer:
 * readline, shells and full-screen TUIs own echoing, cursor movement and edits.
 */
class RemoteTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var engine: TerminalEngine? = null
    private var renderer = createRenderer(DEFAULT_FONT_SIZE_SP)
    private var fontSizeSp = DEFAULT_FONT_SIZE_SP
    private var inputEnabled = false
    private var onTextInput: (String) -> Unit = {}
    private var onRawInput: (ByteArray) -> Unit = {}
    private var onTerminalResize: (
        columns: Int,
        rows: Int,
        cellWidthPx: Int,
        cellHeightPx: Int,
        widthPx: Int,
        heightPx: Int
    ) -> Unit = { _, _, _, _, _, _ -> }
    private var displayedRevision = Long.MIN_VALUE
    private var topRow = 0
    private var userScrolledBack = false
    private var scrollRemainder = 0f
    private var lastTouchX = 1
    private var lastTouchY = 1
    private var singleTapPending = false
    private var lastSentSize: ViewportSize? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                lastTouchX = terminalColumn(event.x)
                lastTouchY = terminalRow(event.y)
                return true
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val terminal = engine
                if (terminal?.isMouseTrackingActive() == true) {
                    val column = terminalColumn(event.x)
                    val row = terminalRow(event.y)
                    terminal.sendMouseEvent(
                        TerminalEmulator.MOUSE_LEFT_BUTTON,
                        column,
                        row,
                        true
                    )
                    terminal.sendMouseEvent(
                        TerminalEmulator.MOUSE_LEFT_BUTTON,
                        column,
                        row,
                        false
                    )
                }
                singleTapPending = true
                return true
            }

            override fun onScroll(
                first: MotionEvent?,
                current: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                scrollRemainder += distanceY
                val lineSpacing = renderer.fontLineSpacing.coerceAtLeast(1)
                val rows = (scrollRemainder / lineSpacing).toInt()
                if (rows != 0) {
                    scrollRemainder -= rows * lineSpacing
                    scrollTerminal(rows)
                }
                return true
            }

            override fun onLongPress(event: MotionEvent) {
                pasteClipboard()
            }
        }
    )

    init {
        setBackgroundColor(TERMINAL_BACKGROUND)
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    fun bind(
        terminalEngine: TerminalEngine,
        revision: Long,
        newFontSizeSp: Int,
        enabled: Boolean,
        textInput: (String) -> Unit,
        rawInput: (ByteArray) -> Unit,
        terminalResize: (
            columns: Int,
            rows: Int,
            cellWidthPx: Int,
            cellHeightPx: Int,
            widthPx: Int,
            heightPx: Int
        ) -> Unit
    ) {
        val engineChanged = engine !== terminalEngine
        engine = terminalEngine
        inputEnabled = enabled
        onTextInput = textInput
        onRawInput = rawInput
        onTerminalResize = terminalResize

        if (engineChanged) {
            topRow = 0
            userScrolledBack = false
            displayedRevision = Long.MIN_VALUE
        }
        if (newFontSizeSp != fontSizeSp) {
            fontSizeSp = newFontSizeSp
            renderer = createRenderer(fontSizeSp)
            lastSentSize = null
            notifyTerminalSize()
        }
        if (revision != displayedRevision) {
            applyScreenUpdate(revision)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(TERMINAL_BACKGROUND)
        engine?.render(canvas, renderer, topRow)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        notifyTerminalSize()
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions =
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_ACTION_NONE

        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                flushEditable()
                return true
            }

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                flushEditable()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                if (inputEnabled) {
                    repeat(leftLength.coerceAtMost(32)) {
                        onRawInput(byteArrayOf(DELETE))
                    }
                }
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            private fun flushEditable() {
                val content: Editable = editable ?: return
                if (content.isEmpty()) return
                val committed = content.toString()
                content.clear()
                if (!inputEnabled) return
                onTextInput(committed.replace('\n', '\r'))
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputEnabled) return super.onKeyDown(keyCode, event)

        val fixed = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(CARRIAGE_RETURN)
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> byteArrayOf(DELETE)
            KeyEvent.KEYCODE_TAB -> byteArrayOf(TAB)
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(ESCAPE)
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN -> engine?.keySequence(keyCode)
            else -> null
        }
        if (fixed != null) {
            onRawInput(fixed)
            return true
        }

        val unicode = event.unicodeChar
        if (unicode > 0) {
            onTextInput(String(Character.toChars(unicode)))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyMultiple(
        keyCode: Int,
        repeatCount: Int,
        event: KeyEvent
    ): Boolean {
        val characters = event.characters
        if (inputEnabled && !characters.isNullOrEmpty()) {
            onTextInput(characters)
            return true
        }
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            scrollRemainder = 0f
        }
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && singleTapPending) {
            singleTapPending = false
            performClick()
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            singleTapPending = false
        }
        return handled
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val direction = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (direction != 0f) {
                scrollTerminal(if (direction > 0) -3 else 3)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        showKeyboard()
        return true
    }

    private fun applyScreenUpdate(revision: Long) {
        val terminal = engine ?: return
        val state = terminal.viewportState(clearScrollCounter = true)
        if (state.alternateBuffer) {
            topRow = 0
            userScrolledBack = false
        } else if (userScrolledBack) {
            topRow = (topRow - state.scrolledRows)
                .coerceIn(-state.activeTranscriptRows, 0)
            if (topRow == 0) userScrolledBack = false
        } else {
            topRow = 0
        }
        displayedRevision = revision
        contentDescription = terminal.visibleText()
        invalidate()
    }

    private fun notifyTerminalSize() {
        if (width <= 0 || height <= 0) return
        val cellWidth = renderer.fontWidth.coerceAtLeast(1f)
        val cellHeight = renderer.fontLineSpacing.coerceAtLeast(1)
        val size = ViewportSize(
            columns = (width / cellWidth).toInt().coerceAtLeast(MIN_COLUMNS),
            rows = (height / cellHeight).coerceAtLeast(MIN_ROWS),
            cellWidthPx = cellWidth.roundToInt().coerceAtLeast(1),
            cellHeightPx = cellHeight,
            widthPx = width,
            heightPx = height
        )
        if (size == lastSentSize) return
        lastSentSize = size
        onTerminalResize(
            size.columns,
            size.rows,
            size.cellWidthPx,
            size.cellHeightPx,
            size.widthPx,
            size.heightPx
        )
    }

    private fun scrollTerminal(rowsDown: Int) {
        if (rowsDown == 0) return
        val terminal = engine ?: return
        val state = terminal.viewportState()
        when {
            terminal.isMouseTrackingActive() -> {
                val button = if (rowsDown < 0) {
                    TerminalEmulator.MOUSE_WHEELUP_BUTTON
                } else {
                    TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                }
                repeat(abs(rowsDown).coerceAtMost(MAX_SCROLL_EVENTS)) {
                    terminal.sendMouseEvent(button, lastTouchX, lastTouchY, true)
                }
            }

            state.alternateBuffer -> {
                val keyCode = if (rowsDown < 0) {
                    KeyEvent.KEYCODE_DPAD_UP
                } else {
                    KeyEvent.KEYCODE_DPAD_DOWN
                }
                terminal.keySequence(keyCode)?.let { sequence ->
                    repeat(abs(rowsDown).coerceAtMost(MAX_SCROLL_EVENTS)) {
                        onRawInput(sequence)
                    }
                }
            }

            else -> {
                topRow = (topRow + rowsDown)
                    .coerceIn(-state.activeTranscriptRows, 0)
                userScrolledBack = topRow < 0
                invalidate()
            }
        }
    }

    private fun showKeyboard() {
        if (!inputEnabled) return
        requestFocus()
        post {
            val inputManager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun pasteClipboard() {
        if (!inputEnabled) return
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
            ?: return
        engine?.paste(text)
        Toast.makeText(context, "已粘贴到终端", Toast.LENGTH_SHORT).show()
    }

    private fun terminalColumn(x: Float): Int =
        (x / renderer.fontWidth.coerceAtLeast(1f)).toInt() + 1

    private fun terminalRow(y: Float): Int =
        (y / renderer.fontLineSpacing.coerceAtLeast(1)).toInt() + 1

    private fun createRenderer(sizeSp: Int): TerminalRenderer {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp.toFloat(),
            resources.displayMetrics
        ).roundToInt()
        return TerminalRenderer(sizePx, Typeface.MONOSPACE)
    }

    private data class ViewportSize(
        val columns: Int,
        val rows: Int,
        val cellWidthPx: Int,
        val cellHeightPx: Int,
        val widthPx: Int,
        val heightPx: Int
    )

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 14
        const val MIN_COLUMNS = 4
        const val MIN_ROWS = 4
        const val MAX_SCROLL_EVENTS = 24
        const val TERMINAL_BACKGROUND = 0xFF101516.toInt()
        const val ESCAPE: Byte = 0x1B
        const val TAB: Byte = 0x09
        const val CARRIAGE_RETURN: Byte = 0x0D
        const val DELETE: Byte = 0x7F
    }
}
