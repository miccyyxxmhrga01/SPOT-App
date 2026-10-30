package com.example.spotapp.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** SPOT AI's answer for one post's media, from the talent-ai Edge Function. */
@Serializable
data class TalentPredictionResponse(
    @SerialName("success")
    val success: Boolean = false,

    @SerialName("prediction")
    val prediction: String = "",

    @SerialName("raw_class")
    val rawClass: String = "",

    @SerialName("spot_category")
    val spotCategory: String = "",

    @SerialName("confidence")
    val confidence: Double = 0.0,

    @SerialName("confidence_percent")
    val confidencePercent: Double = 0.0,

    @SerialName("recognized_talent")
    val recognizedTalent: Boolean = false,

    @SerialName("needs_user_confirmation")
    val needsUserConfirmation: Boolean = true,

    @SerialName("message")
    val message: String = "",

    @SerialName("suggestions")
    val suggestions: List<TalentSuggestion> = emptyList()
)

@Serializable
data class TalentSuggestion(
    @SerialName("tag")
    val tag: String = "",

    @SerialName("raw_class")
    val rawClass: String = "",

    @SerialName("spot_category")
    val spotCategory: String = "",

    @SerialName("confidence")
    val confidence: Double = 0.0,

    @SerialName("confidence_percent")
    val confidencePercent: Double = 0.0
)