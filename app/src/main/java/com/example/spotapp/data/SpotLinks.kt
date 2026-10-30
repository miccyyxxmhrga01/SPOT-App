package com.example.spotapp.data

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * SPOT's share links: https://spot-bisu.web.app/p/<post>, /e/<event> and /u/<member id>. Android opens them in SPOT
 * (the verified intent filter on MainActivity, with hosting/.well-known/assetlinks.json); the page at the same address
 * helps people who don't have SPOT yet. The site is the Firebase Hosting site "spot-bisu" in project login-a39d3.
 */
object SpotLinks {
    const val HOST = "spot-bisu.web.app"

    enum class Kind(val path: String) { POST("p"), EVENT("e"), PROFILE("u") }

    data class Link(val kind: Kind, val id: String)

    fun post(id: String) = url(Kind.POST, id)
    fun event(id: String) = url(Kind.EVENT, id)
    fun profile(uid: String) = url(Kind.PROFILE, uid)

    private fun url(kind: Kind, id: String) = "https://$HOST/${kind.path}/$id"

    /** A link someone tapped. Navigation opens it once a member is signed in, then clears it. */
    val pending = MutableStateFlow<Link?>(null)

    fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.data?.let(::parse) ?: return
        // Read once: a recreated activity keeps its intent and must not open the link again.
        intent.data = null
        pending.value = link
    }

    fun parse(uri: Uri): Link? {
        if (uri.scheme != "https" || uri.host != HOST) return null
        val parts = uri.pathSegments
        if (parts.size != 2) return null
        val kind = Kind.entries.firstOrNull { it.path == parts[0] } ?: return null
        val id = parts[1].takeIf { it.matches(ID) } ?: return null
        return Link(kind, id)
    }

    private val ID = Regex("[A-Za-z0-9_-]{1,128}")
}
