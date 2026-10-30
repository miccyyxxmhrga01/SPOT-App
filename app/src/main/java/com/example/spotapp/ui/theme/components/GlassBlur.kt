package com.example.spotapp.ui.theme.components

/** Three separable box passes approximate a Gaussian; work is linear in the small sample size. */
internal fun blurGlassPixels(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && input.size == width * height && radius >= 0)
    if (radius == 0) return input.copyOf()
    var source = input.copyOf()
    var target = IntArray(input.size)
    val window = radius * 2 + 1
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            val lines = if (horizontal) height else width
            val length = if (horizontal) width else height
            for (line in 0 until lines) {
                fun index(position: Int) = if (horizontal) line * width + position.coerceIn(0, length - 1)
                    else position.coerceIn(0, length - 1) * width + line
                var a = 0; var r = 0; var g = 0; var b = 0
                fun accumulate(pixel: Int, sign: Int) {
                    a += (pixel ushr 24) * sign; r += ((pixel ushr 16) and 255) * sign
                    g += ((pixel ushr 8) and 255) * sign; b += (pixel and 255) * sign
                }
                for (position in -radius..radius) accumulate(source[index(position)], 1)
                for (position in 0 until length) {
                    target[index(position)] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                    accumulate(source[index(position - radius)], -1)
                    accumulate(source[index(position + radius + 1)], 1)
                }
            }
            val swap = source; source = target; target = swap
        }
    }
    return source
}
