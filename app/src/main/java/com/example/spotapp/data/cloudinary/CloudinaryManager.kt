package com.example.spotapp.data.cloudinary

import android.content.Context
import com.cloudinary.android.MediaManager

object CloudinaryManager {

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return

        val config = mapOf(
            "cloud_name" to "jcuzzxsz"
        )

        MediaManager.init(
            context.applicationContext,
            config
        )

        initialized = true
    }
}