package com.example.spotapp.data

import java.util.Locale

// Chosen at setup; the database keeps completed account types fixed and gates event creation.
// Spotter is an account category, not a verified professional credential.
enum class AccountType(val storageValue: String, val label: String) {
    STUDENT("student", "Creator"),
    TALENT_SEEKER("talent_seeker", "Spotter");

    companion object {
        fun fromStorage(value: String?): AccountType? = entries.firstOrNull { it.storageValue == value }
    }
}

data class ProfileDetails(
    val accountType: AccountType,
    val displayName: String = "",
    val username: String = "",
    val course: String = "",
    val talents: List<String> = emptyList(),
    val organization: String = "",
    val seekingTalents: List<String> = emptyList(),
    val bio: String = ""
) {
    fun identityError(): String? = when {
        displayName.isBlank() -> "Enter the name you want people to see."
        displayName.trim().length > 60 -> "Keep your display name to 60 characters."
        !Regex("[a-z0-9_]{3,30}").matches(username.trim().lowercase(Locale.ROOT)) ->
            "Use 3–30 letters, numbers or underscores for your username."
        else -> null
    }

    fun detailsError(): String? = when {
        accountType == AccountType.STUDENT && course.isBlank() -> "Choose your course."
        accountType == AccountType.STUDENT && talents.none { it.isNotBlank() } -> "Choose at least one talent."
        accountType == AccountType.STUDENT && course.trim().length > 120 -> "Keep your course to 120 characters."
        accountType == AccountType.TALENT_SEEKER && organization.trim().length > 80 ->
            "Keep your organization name to 80 characters."
        else -> null
    }

    fun validationError(): String? = identityError() ?: detailsError()
        ?: if (bio.trim().length > 160) "Keep your bio to 160 characters." else null

    fun firestoreFields(): Map<String, Any> = mapOf(
        "accountType" to accountType.storageValue,
        "displayName" to displayName.trim(),
        "username" to username.trim().lowercase(Locale.ROOT),
        "course" to if (accountType == AccountType.STUDENT) course.trim() else "",
        "talentCategories" to if (accountType == AccountType.STUDENT) clean(talents) else emptyList<String>(),
        "organization" to if (accountType == AccountType.TALENT_SEEKER) organization.trim() else "",
        "seekingTalents" to if (accountType == AccountType.TALENT_SEEKER) clean(seekingTalents) else emptyList<String>(),
        "bio" to bio.trim()
    )

    private fun clean(values: List<String>) = values.map(String::trim).filter(String::isNotEmpty).distinct()
}
