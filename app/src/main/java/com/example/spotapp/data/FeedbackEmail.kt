package com.example.spotapp.data

/**
 * Feedback and bug reports go straight to SPOT's team by email, from the sender's own email app, with what's needed
 * to follow up: who sent it, the SPOT version and the phone.
 */
object FeedbackEmail {
    const val ADDRESS = "rylcaybinmicdah@gmail.com"

    fun subject(bug: Boolean, version: String) = if (bug) "SPOT bug report · $version" else "SPOT feedback · $version"

    fun error(message: String): String? = when {
        message.trim().length < 10 -> "Write at least 10 characters so we can understand it."
        message.trim().length > 2000 -> "Keep it under 2,000 characters."
        else -> null
    }

    fun body(message: String, sender: String, version: String, device: String): String =
        "${message.trim()}\n\n—\nFrom: $sender\nSPOT $version · $device"
}
