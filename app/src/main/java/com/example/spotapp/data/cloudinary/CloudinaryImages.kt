package com.example.spotapp.data.cloudinary

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Scale
import coil3.size.pxOrElse

// Cloudinary makes and stores one copy per distinct size, so display sizes are rounded up to a few steps.
private val sizeSteps = intArrayOf(160, 320, 640, 1080, 1440, 2560)
private val plainUpload = Regex("^v\\d+/")

/**
 * The URL of a smaller WebP copy of a Cloudinary photo shown in a [width] × [height] px box (0 when unknown),
 * or null to load [url] unchanged. [crop] fills the box like ContentScale.Crop; otherwise the photo fits inside it.
 * Photos are never enlarged, and Cloudinary serves each copy from its CDN after making it once.
 */
fun cloudinaryDisplayUrl(url: String, width: Int, height: Int, crop: Boolean): String? {
    val host = "https://res.cloudinary.com/"
    val marker = "/image/upload/"
    if (!url.startsWith(host)) return null
    val split = url.indexOf(marker, host.length).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    // Only plain uploads (".../image/upload/v123/...") are resized; URLs that already carry a transformation are left alone.
    if (!plainUpload.containsMatchIn(url.substring(split))) return null
    val longest = maxOf(width, height)
    if (longest <= 0) return null
    val step = sizeSteps.firstOrNull { it >= longest } ?: sizeSteps.last()
    fun scaled(side: Int) = (side.toLong() * step / longest).toInt().coerceAtLeast(1)
    val box = when {
        width > 0 && height > 0 -> "w_${scaled(width)},h_${scaled(height)},${if (crop) "c_lfill" else "c_limit"}"
        width > 0 -> "w_${scaled(width)},c_limit"
        else -> "h_${scaled(height)},c_limit"
    }
    return url.substring(0, split) + "f_webp,q_auto,$box/" + url.substring(split)
}

/** Loads Cloudinary photos at the size they're shown instead of the full upload, saving Cloudinary bandwidth and the viewer's data. */
class CloudinaryImageInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed()
        val sized = cloudinaryDisplayUrl(url, chain.size.width.pxOrElse { 0 }, chain.size.height.pxOrElse { 0 },
            crop = chain.request.scale == Scale.FILL) ?: return chain.proceed()
        val result = chain.withRequest(chain.request.newBuilder().data(sized).build()).proceed()
        // If Cloudinary can't make the smaller copy (for example, the monthly transformation quota is used up), show the original.
        return if (result is ErrorResult) chain.proceed() else result
    }
}
