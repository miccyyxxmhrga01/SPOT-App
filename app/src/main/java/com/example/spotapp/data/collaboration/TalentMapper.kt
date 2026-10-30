package com.example.spotapp.data.collaboration

import com.example.spotapp.data.SpotTalentCategories

/** Collaboration talents use SPOT's category names, so they read the same as profile and post categories. */
object TalentMapper {

    fun fromPrediction(prediction: String): String? = when (val key = prediction.trim().lowercase()) {
        "singer", "vocals", "vocal" -> "Singing"
        "guitarist" -> "Guitar"
        "pianist", "keyboard" -> "Piano"
        "drum", "drummer" -> "Drums"
        "illustrator" -> "Drawing"
        "painter" -> "Painting"
        "dance", "dancer" -> "Dancing"
        "photographer" -> "Photography"
        "chef", "cook" -> "Cooking"
        "coder", "programmer", "programming" -> "Coding"
        "artist" -> "Art"
        "musician" -> "Music"
        else -> SpotTalentCategories.firstOrNull { it.lowercase() == key }
    }
}
