package com.example.spotapp

import com.example.spotapp.data.*
import org.junit.Assert.*
import org.junit.Test

class WorkFlowTest {
    private fun request(kind: String = "invitation", status: String = "pending") = TalentRequest("id", kind, "source", "seeker", "creator", "Campus event", "A clear role", status, 0)
    @Test fun invitationRecipientAndApplicationReviewerHaveOppositeRoles() {
        assertTrue(request().allows("creator", "accepted"))
        assertFalse(request().allows("seeker", "accepted"))
        assertTrue(request("application").allows("seeker", "accepted"))
        assertFalse(request("application").allows("creator", "accepted"))
        assertTrue(request().allows("seeker", "withdrawn"))
        assertTrue(request("application").allows("creator", "withdrawn"))
        assertFalse(request().allows("stranger", "declined"))
    }
    @Test fun terminalResponsesCannotBeChangedByEitherParty() {
        for (kind in listOf("invitation", "application")) for (state in listOf("accepted", "declined", "withdrawn"))
            for (actor in listOf("seeker", "creator")) for (next in listOf("pending", "accepted", "declined", "withdrawn"))
                assertFalse(request(kind, state).allows(actor, next))
    }
    @Test fun expiredAndIncompleteBriefsCannotBePublished() {
        val draft = OpportunityDraft("Campus dancer", "audition", "Dance for our campus festival", "Dancing", "Campus", 2000)
        assertNull(draft.problem(1000))
        assertNotNull(draft.problem(2000))
        assertNotNull(draft.copy(description = " ").problem(1000))
        assertNotNull(draft.copy(location = "").problem(1000))
        assertNotNull(draft.copy(title = "x".repeat(101)).problem(1000))
    }
    @Test fun deadlineBoundaryAndClosedStatusBothStopApplications() {
        val o = Opportunity("id", "seeker", "Role", "job", "Brief", "Music", "Campus", 2000, "open", 0)
        assertTrue(o.isOpen(1999)); assertFalse(o.isOpen(2000)); assertFalse(o.copy(status = "closed").isOpen(1000))
    }
}
