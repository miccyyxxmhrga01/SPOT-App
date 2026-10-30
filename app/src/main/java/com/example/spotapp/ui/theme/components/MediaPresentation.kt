package com.example.spotapp.ui.theme.components

import java.util.Locale

internal fun spotCompactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000f)
    else -> value.coerceAtLeast(0).toString()
}

/** Keep portraits prominent and panoramas usable without distorting the media inside. */
internal fun previewAspectRatio(width: Int, height: Int): Float =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(.65f, 2f) else 1f
