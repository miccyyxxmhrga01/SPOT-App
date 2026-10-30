package com.example.spotapp

import com.example.spotapp.ui.theme.components.blurGlassPixels
import com.example.spotapp.ui.theme.components.previewAspectRatio
import org.junit.Assert.*
import org.junit.Test

class GlassBlurTest {
    @Test fun uniformColorAndAlphaRemainUnchanged() {
        val color = 0x80705020.toInt()
        val input = IntArray(35) { color }
        assertArrayEquals(input, blurGlassPixels(input, 7, 5, 3))
    }
    @Test fun sharpEdgeBecomesGradientWithoutChangingInputOrDimensions() {
        val pixels = IntArray(48 * 6) { if (it % 48 < 24) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val original = pixels.copyOf()
        val blurred = blurGlassPixels(pixels, 48, 6, 3)
        assertArrayEquals(original, pixels)
        assertEquals(pixels.size, blurred.size)
        assertEquals(0, blurred[0] and 255)
        assertEquals(255, blurred[47] and 255)
        assertTrue((blurred[23] and 255) in 1..254)
        assertTrue((blurred[24] and 255) > (blurred[23] and 255))
        assertTrue(blurred.all { it ushr 24 == 255 })
    }
    @Test fun tinyCaptureAndNoBlurAreSafe() {
        assertArrayEquals(intArrayOf(0xFFCCAA33.toInt()), blurGlassPixels(intArrayOf(0xFFCCAA33.toInt()), 1, 1, 3))
        val pixels = intArrayOf(1, 2, 3)
        assertArrayEquals(pixels, blurGlassPixels(pixels, 3, 1, 0))
    }
    @Test fun previewUsesMediaProportionsWithinUsableBounds() {
        assertEquals(.8f, previewAspectRatio(800, 1000), .001f)
        assertEquals(1.6f, previewAspectRatio(1600, 1000), .001f)
        assertEquals(.65f, previewAspectRatio(100, 3000), .001f)
        assertEquals(2f, previewAspectRatio(3000, 100), .001f)
        assertEquals(1f, previewAspectRatio(0, 0), .001f)
    }
}
