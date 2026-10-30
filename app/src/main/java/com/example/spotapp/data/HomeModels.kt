package com.example.spotapp.data

data class SpotUser(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val course: String = "",
    val talentCategories: List<String> = emptyList(),
    val bio: String = "",
    val profileImageUrl: String = "",

    // Used only for temporary local fake users
    val localProfileImageRes: Int? = null,

    val isFake: Boolean = false,
    val accountType: AccountType? = null,
    val organization: String = "",
    val seekingTalents: List<String> = emptyList(),
    val pronouns: String = "", val links: String = "", val gender: String = "",
    val isPrivate: Boolean = false, val privacyUpdating: Boolean = false,
    val openToCollab: Boolean = true
)


enum class PostMediaType {
    IMAGE,
    VIDEO
}


data class SpotPost(
    val id: String = "",
    val userId: String = "",

    val caption: String = "",

    val talentCategory: String = "",

    val tags: List<String> = emptyList(),

    val mediaType: PostMediaType = PostMediaType.IMAGE,

    // Cloudinary URLs
    val mediaUrls: List<String> = emptyList(),

    // Used only for fake local posts
    val localImageResources: List<Int> = emptyList(),

    val likesCount: Long = 0,

    val commentsCount: Long = 0,

    val sharesCount: Long = 0,

    val aiCaption: Boolean = false,

    val aiTagged: Boolean = false,

    val createdAt: Long = 0,

    val user: SpotUser? = null,

    val isFake: Boolean = false
)
