package com.example.spotapp.ui.theme.components

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.example.spotapp.data.MediaLook

/** The look as a Compose color filter for images, or null when it changes nothing. */
fun MediaLook.colorFilter(): ColorFilter? = if (isOriginal) null else ColorFilter.colorMatrix(ColorMatrix(colorMatrix()))
