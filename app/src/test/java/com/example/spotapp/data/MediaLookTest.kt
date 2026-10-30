package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class MediaLookTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    /** Applies a 4×5 matrix to an opaque RGB color (0–255), as Android's ColorMatrixColorFilter does. */
    private fun apply(matrix: FloatArray, r: Float, g: Float, b: Float) = FloatArray(3) { row ->
        (matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b + matrix[row * 5 + 3] * 255f + matrix[row * 5 + 4]).coerceIn(0f, 255f)
    }

    @Test fun theOriginalLookChangesNothing() {
        assertTrue(MediaLook().isOriginal)
        assertArrayEquals(identity, MediaLook().colorMatrix(), 1e-5f)
    }

    @Test fun monoTurnsColorsGrey() {
        val (r, g, b) = apply(MediaLook("mono").colorMatrix(), 200f, 40f, 90f).toList()
        assertEquals(r, g, .5f); assertEquals(g, b, .5f)
    }

    @Test fun adjustmentsMoveColorsTheRightWay() {
        val grey = floatArrayOf(128f, 128f, 128f)
        val brighter = apply(MediaLook(brightness = .5f).colorMatrix(), 128f, 128f, 128f)
        assertTrue(brighter.indices.all { brighter[it] > grey[it] })
        val warm = apply(MediaLook(warmth = .8f).colorMatrix(), 128f, 128f, 128f)
        assertTrue(warm[0] > 128f && warm[2] < 128f)
        // More contrast pushes dark and light apart; mid-grey stays put.
        val contrast = MediaLook(contrast = .6f).colorMatrix()
        assertTrue(apply(contrast, 40f, 40f, 40f)[0] < 40f && apply(contrast, 220f, 220f, 220f)[0] > 220f)
        assertEquals(128f, apply(contrast, 128f, 128f, 128f)[0], .5f)
    }

    @Test fun unknownOrBrokenLooksFallBackSafely() {
        assertTrue(MediaLook("not-a-filter", brightness = Float.NaN).isOriginal)
        assertEquals(1f, MediaLook(contrast = 7f).normalized().contrast)
        assertFalse(MediaLook("vivid").isOriginal)
    }
}
