package com.example.spotapp.data.collaboration

import com.example.spotapp.data.SpotTalentCategories
import com.example.spotapp.data.ai.TalentSuggestion
import com.example.spotapp.data.ai.composerCategory
import org.junit.Assert.*
import org.junit.Test

class TalentMapperTest {
    @Test fun collabTalentsUseSpotCategoryNames() {
        SpotTalentCategories.forEach { assertEquals(it, TalentMapper.fromPrediction(it.lowercase())) }
        assertEquals("Singing", TalentMapper.fromPrediction("Singer"))
        assertEquals("Guitar", TalentMapper.fromPrediction(" guitarist "))
        assertEquals("Piano", TalentMapper.fromPrediction("keyboard"))
        assertEquals("Cooking", TalentMapper.fromPrediction("Chef"))
        assertEquals("Coding", TalentMapper.fromPrediction("programmer"))
        assertNull(TalentMapper.fromPrediction("juggling"))
    }

    @Test fun everyAiSuggestionCanBeSavedForCollabs() {
        (SpotTalentCategories.map { it.lowercase() } + "keyboard").forEach { rawClass ->
            val suggestion = TalentSuggestion(rawClass = rawClass)
            assertEquals(suggestion.composerCategory(), TalentMapper.fromPrediction(rawClass))
        }
    }
}
