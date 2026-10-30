package com.example.spotapp.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Compact shared steps for legacy screens that specify a size explicitly.
 * System font scaling still applies on top, capped at 1.15 by MainActivity. Already-small labels and miniature
 * illustration text retain their size.
 */
val Int.spotSp: TextUnit get() = toFloat().spotSp
val Double.spotSp: TextUnit get() = toFloat().spotSp
val Float.spotSp: TextUnit get() = when {
    this <= 10 -> this.sp
    this <= 11 -> 11.sp
    this <= 12 -> 11.5.sp
    this <= 13 -> 12.sp
    this <= 14 -> 13.sp
    this <= 16 -> 14.sp
    this <= 18 -> 16.sp
    this <= 20 -> 18.sp
    this <= 22 -> 20.sp
    this <= 25 -> 23.sp
    this <= 28 -> 26.sp
    this <= 30 -> 28.sp
    this <= 34 -> 30.sp
    this <= 40 -> 36.sp
    else -> 44.sp
}
