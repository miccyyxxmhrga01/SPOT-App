package com.example.spotapp.data

data class Opportunity(
    val id: String, val seekerId: String, val title: String, val kind: String,
    val description: String, val category: String, val location: String,
    val deadline: Long, val status: String, val createdAt: Long
) {
    fun isOpen(now: Long = System.currentTimeMillis()) = status == "open" && deadline > now
}

data class OpportunityDraft(val title: String = "", val kind: String = "job",
    val description: String = "", val category: String = "", val location: String = "", val deadline: Long = 0) {
    fun problem(now: Long = System.currentTimeMillis()): String? = when {
        title.trim().length !in 2..100 -> "Enter a title between 2 and 100 characters."
        kind !in listOf("job", "audition") -> "Choose a job or audition."
        description.trim().length !in 10..3000 -> "Describe the role and expectations (10–3,000 characters)."
        category.trim().length !in 1..60 -> "Choose a talent category."
        location.trim().length !in 1..160 -> "Enter a location or Online."
        deadline <= now -> "Choose a future application deadline."
        else -> null
    }
}

/** Invitations and applications are requests, not payments or employment contracts. */
data class TalentRequest(val id: String, val kind: String, val sourceId: String,
    val seekerId: String, val creatorId: String, val title: String, val details: String,
    val status: String, val createdAt: Long) {
    fun otherUid(me: String) = if (me == seekerId) creatorId else seekerId
    fun canRespond(me: String) = status == "pending" &&
        me == if (kind == "invitation") creatorId else seekerId
    fun canWithdraw(me: String) = status == "pending" &&
        me == if (kind == "invitation") seekerId else creatorId
    fun allows(me: String, next: String) = when (next) {
        "accepted", "declined" -> canRespond(me)
        "withdrawn" -> canWithdraw(me)
        else -> false
    }
}
