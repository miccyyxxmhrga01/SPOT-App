package com.example.spotapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.spotapp.R

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun spotText(size: Number, line: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.toFloat().sp, lineHeight = line.sp, letterSpacing = tracking
)

// Inter reads tighter and more editorial at large sizes with slight negative tracking.
val Typography = Typography(
    displayLarge = spotText(44, 52, FontWeight.Bold, (-0.8).sp),
    displayMedium = spotText(36, 44, FontWeight.Bold, (-0.6).sp),
    displaySmall = spotText(30, 38, FontWeight.Bold, (-0.5).sp),
    headlineLarge = spotText(26, 34, FontWeight.Bold, (-0.4).sp),
    headlineMedium = spotText(23, 31, FontWeight.Bold, (-0.35).sp),
    headlineSmall = spotText(20, 28, FontWeight.SemiBold, (-0.3).sp),
    titleLarge = spotText(18, 26, FontWeight.SemiBold, (-0.2).sp),
    titleMedium = spotText(14, 21, FontWeight.SemiBold, (-0.1).sp),
    titleSmall = spotText(13, 19, FontWeight.SemiBold),
    bodyLarge = spotText(14, 21),
    bodyMedium = spotText(12, 19),
    bodySmall = spotText(11.5, 17),
    labelLarge = spotText(12, 19, FontWeight.SemiBold),
    labelMedium = spotText(11.5, 16, FontWeight.Medium),
    labelSmall = spotText(11, 16, FontWeight.Medium, 0.1.sp)
)
