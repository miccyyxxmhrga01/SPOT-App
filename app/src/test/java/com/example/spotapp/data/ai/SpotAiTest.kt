package com.example.spotapp.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException

class SpotAiTest {
    @Test fun modelClassesMapOntoComposerCategories() {
        assertEquals("Guitar", TalentSuggestion(rawClass = "guitar").composerCategory())
        assertEquals("Dancing", TalentSuggestion(rawClass = "dancing").composerCategory())
        assertEquals("Piano", TalentSuggestion(rawClass = "keyboard").composerCategory())
        assertNull(TalentSuggestion(rawClass = "other").composerCategory())
        assertNull(TalentSuggestion(rawClass = "juggling").composerCategory())
    }

    @Test fun newModelClassesMapOntoComposerCategories() {
        mapOf("basketball" to "Basketball", "volleyball" to "Volleyball", "futsal" to "Futsal", "cooking" to "Cooking",
            "photography" to "Photography", "coding" to "Coding").forEach { (rawClass, category) ->
            assertEquals(category, TalentSuggestion(rawClass = rawClass).composerCategory())
        }
    }

    // The talent-ai Edge Function answers with SPOT's own category names, in the Render service's shape.
    @Test fun talentAiRepliesDecodeIntoComposerCategories() {
        val reply = """{"success":true,"prediction":"Singing","raw_class":"Singing","spot_category":"Singing","confidence":0.9,
            "confidence_percent":90,"recognized_talent":true,"needs_user_confirmation":false,"message":"Looks like Singing.",
            "suggestions":[{"tag":"Singing","raw_class":"Singing","spot_category":"Singing","confidence":0.9,"confidence_percent":90},
            {"tag":"Guitar","raw_class":"Guitar","spot_category":"Guitar","confidence":0.4,"confidence_percent":40}]}"""
        val result = Json { ignoreUnknownKeys = true }.decodeFromString<TalentPredictionResponse>(reply)
        assertTrue(result.recognizedTalent)
        assertEquals(listOf("Singing", "Guitar"), result.suggestions.map { it.composerCategory() })
        assertEquals(40.0, result.suggestions[1].confidencePercent, 0.0)
    }

    @Test fun failuresBecomeReadableMessages() {
        val limit = "SPOT AI has checked a lot of media for you this hour. Choose a category yourself, or try again later."
        assertEquals(limit, spotAiError(IllegalStateException(limit)))
        assertTrue(spotAiError(UnknownHostException()).contains("connection"))
        assertTrue(spotAiError(RuntimeException()).contains("couldn't analyze"))
    }
}
