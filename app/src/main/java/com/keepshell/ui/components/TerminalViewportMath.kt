package com.keepshell.ui.components

import kotlin.math.floor

/**
 * Pure viewport calculations shared by the Android terminal view and unit tests.
 *
 * Termux renders the first row below a small font-metrics offset, so that offset
 * is not available for complete terminal rows. Overview mode keeps a logical
 * terminal usable for desktop-oriented TUIs without permanently changing the
 * user's preferred font size.
 */
internal object TerminalViewportMath {
    fun fontSizeStepFromRenderedPx(
        renderedTextSizePx: Int,
        scaledDensity: Float,
        deltaSp: Int,
        minimumSizeSp: Int,
        maximumSizeSp: Int
    ): Int {
        val renderedSizeSp = if (renderedTextSizePx > 0 && scaledDensity > 0f) {
            floor(renderedTextSizePx / scaledDensity).toInt()
        } else {
            minimumSizeSp
        }
        return adjustedFontSizeSp(
            currentSizeSp = renderedSizeSp,
            deltaSp = deltaSp,
            minimumSizeSp = minimumSizeSp,
            maximumSizeSp = maximumSizeSp
        )
    }

    fun adjustedFontSizeSp(
        currentSizeSp: Int,
        deltaSp: Int,
        minimumSizeSp: Int,
        maximumSizeSp: Int
    ): Int {
        val lowerBound = minOf(minimumSizeSp, maximumSizeSp)
        val upperBound = maxOf(minimumSizeSp, maximumSizeSp)
        return (currentSizeSp + deltaSp).coerceIn(lowerBound, upperBound)
    }

    fun visibleRows(
        heightPx: Int,
        lineSpacingPx: Int,
        verticalOffsetPx: Int,
        minimumRows: Int
    ): Int {
        val safeSpacing = lineSpacingPx.coerceAtLeast(1)
        val drawableHeight = (heightPx - verticalOffsetPx.coerceAtLeast(0))
            .coerceAtLeast(0)
        return (drawableHeight / safeSpacing).coerceAtLeast(minimumRows)
    }

    fun overviewTextSizePx(
        baseTextSizePx: Int,
        viewportWidthPx: Int,
        baseCellWidthPx: Float,
        targetColumns: Int
    ): Int {
        if (
            baseTextSizePx <= 1 ||
            viewportWidthPx <= 0 ||
            baseCellWidthPx <= 0f ||
            targetColumns <= 0
        ) {
            return baseTextSizePx.coerceAtLeast(1)
        }

        val requiredWidth = baseCellWidthPx * targetColumns
        if (requiredWidth <= viewportWidthPx) return baseTextSizePx

        return floor(baseTextSizePx * viewportWidthPx / requiredWidth)
            .toInt()
            .coerceIn(1, baseTextSizePx)
    }
}
