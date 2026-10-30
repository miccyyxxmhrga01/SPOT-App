package com.example.spotapp.data

/** A named starting point for a photo's look; its values add to the member's own adjustments. */
data class LookFilter(val key: String, val label: String, val brightness: Float = 0f, val contrast: Float = 0f,
    val saturation: Float = 0f, val warmth: Float = 0f)

val LookFilters = listOf(
    LookFilter("normal", "Normal"),
    LookFilter("vivid", "Vivid", contrast = .12f, saturation = .35f),
    LookFilter("warm", "Warm", saturation = .1f, warmth = .55f),
    LookFilter("cool", "Cool", saturation = .05f, warmth = -.55f),
    LookFilter("golden", "Golden", brightness = .06f, saturation = .15f, warmth = .35f),
    LookFilter("fade", "Fade", brightness = .1f, contrast = -.3f, saturation = -.25f),
    LookFilter("drama", "Drama", brightness = -.06f, contrast = .4f, saturation = -.1f),
    LookFilter("mono", "Mono", contrast = .12f, saturation = -1f),
)

/**
 * An Instagram-style look: a filter plus brightness, contrast, saturation and warmth, each from -1 to 1.
 * It becomes one color matrix, so the editor preview, the uploaded photo and the story viewer all match.
 */
data class MediaLook(val filter: String = "normal", val brightness: Float = 0f, val contrast: Float = 0f,
    val saturation: Float = 0f, val warmth: Float = 0f) {
    val isOriginal: Boolean get() = normalized() == MediaLook()

    /** Unknown filters and out-of-range values fall back to safe ones, so saved looks from any version still load. */
    fun normalized(): MediaLook = MediaLook(filter.takeIf { key -> LookFilters.any { it.key == key } } ?: "normal",
        clean(brightness), clean(contrast), clean(saturation), clean(warmth))

    /** 4×5 RGBA color matrix, row by row, with offsets in 0–255: what Android and Compose color filters take. */
    fun colorMatrix(): FloatArray {
        val look = normalized()
        val preset = LookFilters.first { it.key == look.filter }
        val b = (look.brightness + preset.brightness).coerceIn(-1f, 1f)
        val c = (look.contrast + preset.contrast).coerceIn(-1f, 1f)
        val s = (look.saturation + preset.saturation).coerceIn(-1f, 1f)
        val w = (look.warmth + preset.warmth).coerceIn(-1f, 1f)
        // Saturation first, then contrast around mid-grey, then brightness and warmth as channel offsets.
        return concat(offsets(r = b * 60f + w * 22f, g = b * 60f + w * 6f, bl = b * 60f - w * 22f), concat(contrast(c), saturation(s)))
    }

    private companion object {
        fun clean(value: Float) = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f

        fun saturation(amount: Float): FloatArray {
            val s = 1f + amount
            val (r, g, b) = Triple(.2126f * (1 - s), .7152f * (1 - s), .0722f * (1 - s))
            return floatArrayOf(r + s, g, b, 0f, 0f, r, g + s, b, 0f, 0f, r, g, b + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        }

        fun contrast(amount: Float): FloatArray {
            val k = 1f + amount * .5f
            val t = 128f * (1f - k)
            return floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f)
        }

        fun offsets(r: Float, g: Float, bl: Float) =
            floatArrayOf(1f, 0f, 0f, 0f, r, 0f, 1f, 0f, 0f, g, 0f, 0f, 1f, 0f, bl, 0f, 0f, 0f, 1f, 0f)

        /** [outer] applied after [inner]. */
        fun concat(outer: FloatArray, inner: FloatArray): FloatArray = FloatArray(20) { index ->
            val row = index / 5; val column = index % 5
            var sum = if (column == 4) outer[row * 5 + 4] else 0f
            for (k in 0..3) sum += outer[row * 5 + k] * inner[k * 5 + column]
            sum
        }
    }
}
