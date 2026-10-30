package com.example.spotapp.data.collaboration

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A real prediction explicitly selected by the creator for collaboration matching. */
data class DetectedCollaborationTalent(val prediction: String, val confidence: Double)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserTalent(
    @SerialName("user_id")
    val userId: String,

    val talent: String,

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val confidence: Double = 1.0,

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val source: String = "profile",

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val confirmed: Boolean = true
)


/** A creator suggested by suggest_collaborators, with the short reason shown on the card. */
@Serializable
data class CollaboratorSuggestion(
    @SerialName("user_id")
    val userId: String,

    @SerialName("display_name")
    val displayName: String,

    val username: String = "",

    @SerialName("avatar_url")
    val avatarUrl: String? = null,

    val talents: List<String> = emptyList(),

    val score: Double = 0.0,

    val reason: String = ""
)

/** Where someone is based, who they'd like to work with, and whether they're suggested to others at all. */
@Serializable
data class CollabPreferences(
    val location: String = "",

    @SerialName("collab_talents")
    val collabTalents: List<String> = emptyList(),

    @SerialName("open_to_collab")
    val openToCollab: Boolean = true
) {
    fun error(): String? = when {
        location.trim().length > 80 -> "Keep your location to 80 characters."
        collabTalents.size > 8 -> "Choose up to 8 talents."
        else -> null
    }
}
