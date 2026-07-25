package com.keepshell.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalViewportMathTest {
    @Test
    fun `zooming in after overview starts from the fitted pixels`() {
        assertEquals(
            7,
            TerminalViewportMath.fontSizeStepFromRenderedPx(
                renderedTextSizePx = 20,
                scaledDensity = 3f,
                deltaSp = 1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        )
    }

    @Test
    fun `many rapid font steps remain bounded`() {
        var size = 14
        repeat(10_000) {
            size = TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = size,
                deltaSp = 1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        }
        assertEquals(32, size)

        repeat(10_000) {
            size = TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = size,
                deltaSp = -1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        }
        assertEquals(6, size)
    }

    @Test
    fun `font zoom stays inside the supported range`() {
        assertEquals(
            6,
            TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = 6,
                deltaSp = -1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        )
        assertEquals(
            32,
            TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = 32,
                deltaSp = 1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        )
    }

    @Test
    fun `font zoom changes one step at a time`() {
        assertEquals(
            13,
            TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = 14,
                deltaSp = -1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        )
        assertEquals(
            15,
            TerminalViewportMath.adjustedFontSizeSp(
                currentSizeSp = 14,
                deltaSp = 1,
                minimumSizeSp = 6,
                maximumSizeSp = 32
            )
        )
    }

    @Test
    fun `visible rows reserve the renderer baseline offset`() {
        assertEquals(
            19,
            TerminalViewportMath.visibleRows(
                heightPx = 984,
                lineSpacingPx = 49,
                verticalOffsetPx = 8,
                minimumRows = 4
            )
        )
    }

    @Test
    fun `visible rows retain the terminal minimum on short viewports`() {
        assertEquals(
            4,
            TerminalViewportMath.visibleRows(
                heightPx = 40,
                lineSpacingPx = 20,
                verticalOffsetPx = 6,
                minimumRows = 4
            )
        )
    }

    @Test
    fun `overview mode scales a narrow phone to the target columns`() {
        assertEquals(
            20,
            TerminalViewportMath.overviewTextSizePx(
                baseTextSizePx = 42,
                viewportWidthPx = 996,
                baseCellWidthPx = 25f,
                targetColumns = 80
            )
        )
    }

    @Test
    fun `overview mode preserves the preferred size when already wide enough`() {
        assertEquals(
            42,
            TerminalViewportMath.overviewTextSizePx(
                baseTextSizePx = 42,
                viewportWidthPx = 2200,
                baseCellWidthPx = 25f,
                targetColumns = 80
            )
        )
    }
}
