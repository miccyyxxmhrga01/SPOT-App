package com.example.spotapp

import com.example.spotapp.data.*
import com.example.spotapp.data.ai.mergeTags
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile
import org.junit.Assert.*
import org.junit.Test

class ComposerArchitectureTest {
    @Test fun manualPostsHaveNoAiClaims() {
        val profile = SpotUiProfile("author", "Creator", "creator", "", "")
        val draft = PostDraft("id", "My campus performance", listOf("Guitar"), listOf("CampusTalent"), emptyList(), "public")
        assertNull(draft.error())
        assertFalse(draft.aiCaption || draft.aiTagged || draft.aiCategorySuggested)
        val ui = SpotUiPost("id", profile, draft.caption, "Guitar", draft.tags)
        assertFalse(ui.aiCaption || ui.aiTagged || ui.aiCategorySuggested)
    }
    @Test fun legacyPostsDefaultToManualAndExplicitMetadataRoundTrips() {
        val row = PostRow(id = "id", userId = "creator", caption = "A photo", audience = "public")
        val manual = PostDocumentMapper.published(row)!!
        assertFalse(manual.aiCaption || manual.aiTagged || manual.aiCategorySuggested)
        val future = PostDocumentMapper.published(row.copy(aiCaption = true, aiTagged = true, aiCategorySuggested = true))!!.asUiPost()
        assertTrue(future.aiCaption && future.aiTagged && future.aiCategorySuggested)
    }
    @Test fun aiHashtagsJoinWithoutDuplicatesOrPassingTheLimit() {
        assertEquals(listOf("CampusTalent", "singing", "music"),
            mergeTags(listOf("CampusTalent"), listOf("#singing", "campustalent", "music", "  ", "x".repeat(61))))
        val full = (1..19).map { "tag$it" }
        assertEquals(full + "one", mergeTags(full, listOf("one", "two")))
        assertEquals((1..20).map { "t$it" }, mergeTags((1..20).map { "t$it" }, listOf("extra")))
    }
    @Test fun postReportReasonsMatchTheServerList() {
        // report_post accepts exactly these values.
        assertEquals(listOf("spam", "nudity", "harassment", "hate", "violence", "false_info", "stolen", "other"),
            PostReportReasons.map { it.first })
    }
    @Test fun eventValidationRejectsPastTimeAndInvalidCapacity() {
        val d = EventDraft("id", "Campus showcase", "Perform on stage", "", "Singing", 2000, "Campus hall", "SPOT Society", null, TalentFee.FREE)
        assertNull(d.error(1000)); assertNotNull(d.error(3000))
        assertNotNull(d.copy(participantLimit = 0).error(1000)); assertNotNull(d.copy(participantLimit = 100001).error(1000))
        assertNull(d.copy(participantLimit = 1).error(1000))
    }
    @Test fun eventsNeedATalentFeeChoiceAndABudgetOnlyWhenPaid() {
        val d = EventDraft("id", "Campus showcase", "Perform on stage", "", "Singing", 2000, "Campus hall", "SPOT Society", null)
        assertNotNull(d.error(1000))
        assertNull(d.copy(talentFee = TalentFee.PAID).error(1000))
        assertNull(d.copy(talentFee = TalentFee.PAID, talentFeeBudget = "₱1,500 per act").error(1000))
        assertNotNull(d.copy(talentFee = TalentFee.PAID, talentFeeBudget = "x".repeat(81)).error(1000))
        assertEquals("For Free Only", TalentFee.label(TalentFee.FREE))
        assertEquals("With Talent Fee Budget", TalentFee.label(TalentFee.PAID))
    }
    @Test fun eachAcceptedCreatorTakesOneSlot() {
        val e = CampusEvent("e", "Gig", "", "", "Singing", 0, "Hall", "Org", "o", "Org", "", 3, 2, 0)
        assertEquals("2 accepted · 1 slot left", e.slotSummary); assertFalse(e.isFull)
        assertEquals("3 accepted · 0 slots left", e.copy(participantCount = 3).slotSummary); assertTrue(e.copy(participantCount = 3).isFull)
        assertEquals("2 accepted · Open slots", e.copy(participantLimit = null).slotSummary)
    }
    @Test fun groupsRequireRealDescriptionCategoryAndValidPrivacy() {
        val d = GroupDraft("id", "Campus artists", "Paint together", "", "Painting", "private")
        assertNull(d.error()); assertNotNull(d.copy(privacy = "unknown").error())
        assertNotNull(d.copy(name = " ").error()); assertNotNull(d.copy(description = "").error())
    }
}
