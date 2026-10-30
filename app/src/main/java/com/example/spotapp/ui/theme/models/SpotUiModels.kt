package com.example.spotapp.ui.theme.models

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.example.spotapp.data.AccountType


data class SpotUiProfile(
    val uid: String,
    val displayName: String,
    val username: String,
    val talent: String,
    val course: String,
    val profileImageUrl: String = "",
    val isVerifiedTalent: Boolean = false,
    val accountType: AccountType? = null,
    val organization: String = "",
    val bio: String = "", val talentCategories: List<String> = emptyList(),
    val seekingTalents: List<String> = emptyList(), val pronouns: String = "",
    val links: String = "", val gender: String = "", val isPrivate: Boolean = false,
    val privacyUpdating: Boolean = false,
    /** Whether this Creator welcomes collaboration invites. */
    val openToCollab: Boolean = true
)


data class SpotUiPost(
    val id: String,
    val user: SpotUiProfile,
    val caption: String,
    val category: String,
    val tags: List<String>,

    val mediaUri: String? = null,

    // Local post images
    val localImageResources: List<Int> = emptyList(),

    val postedTime: String = "Just now",

    // Local video
    @RawRes
    val localVideoRes: Int? = null,

    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,

    val liked: Boolean = false,
    val saved: Boolean = false,

    val aiCaption: Boolean = false,
    val aiTagged: Boolean = false,
    val aiCategorySuggested: Boolean = false,

    // Owner controls: a hidden like count shows to the owner only, "Edited" after a change, pinned on the profile.
    val likesHidden: Boolean = false,
    val edited: Boolean = false,
    val pinned: Boolean = false
)


enum class SpotRoute {
    HOME,
    DISCOVER,
    NEW_POST,
    NOTIFICATIONS,
    PROFILE,
    SETTINGS,
    SEARCH,
    MESSAGES_CHATS,
    MESSAGES_GROUPS,
    COLLAB_REQUESTS,
    REELS
}
