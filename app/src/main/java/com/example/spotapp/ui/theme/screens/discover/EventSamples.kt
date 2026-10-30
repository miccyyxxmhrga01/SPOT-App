package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.data.AccountType
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.data.EventApplication
import com.example.spotapp.data.OrganizedEvent
import com.example.spotapp.data.Applicant
import com.example.spotapp.data.TalentFee
import com.example.spotapp.ui.theme.models.SpotUiProfile
import java.util.Calendar

/**
 * Made-up people and events for the event screens' previews and their review test, matching the redesign mock.
 * Dates count from now, so the events stay upcoming.
 */
internal object EventSamples {
    val spotter = SpotUiProfile("sample-ben", "Ben Reyes", "ben", "", "", accountType = AccountType.TALENT_SEEKER,
        organization = "Indie Studio")
    val creator = SpotUiProfile("sample-ana", "Ana Cruz", "ana_c", "Singing", "BS Music", accountType = AccountType.STUDENT,
        talentCategories = listOf("Singing", "Guitar"))

    /** [days] from now at [hour]:[minute]. */
    fun at(days: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, days); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun acousticNight(accepted: Long = 5) = CampusEvent("sample-acoustic", "Acoustic Night at the Quad",
        "An unplugged night of OPM and indie covers on the Quad. Bring your own instrument; there's a mic and a small PA.", "",
        "Singing", at(11, 19), "Main Quad, North Campus", "Indie Studio", spotter.uid, spotter.displayName, "", 8, accepted, 0,
        TalentFee.PAID, "₱1,500 per act")

    fun events(): List<CampusEvent> = listOf(
        acousticNight(),
        CampusEvent("sample-openmic", "Open Mic Fridays", "Sign up at the door.", "", "Music", at(18, 18), "Student Center", "Music Club",
            "sample-org2", "Lia Tan", "", null, 3, 0, TalentFee.FREE),
        CampusEvent("sample-bands", "Battle of the Bands", "Three songs per band.", "", "Drums", at(19, 17), "Gym", "Student Council",
            "sample-org3", "Paolo Cruz", "", 6, 5, 0, TalentFee.PAID, "₱3,000 per band"),
        CampusEvent("sample-sunset", "Sunset Sessions", "Acoustic sets at golden hour.", "", "Guitar", at(26, 17, 30), "Rooftop Deck",
            "Sunset Co.", "sample-org4", "Mara Lim", "", 8, 4, 0, TalentFee.PAID, "₱1,000"),
        CampusEvent("sample-poetry", "Poetry & Piano", "Readings with live piano.", "", "Piano", at(32, 15), "Library Hall", "Lit Society",
            "sample-org5", "Jo Santos", "", 4, 4, 0, TalentFee.FREE),
        CampusEvent("sample-fair", "Campus Fair Showcase", "Dance crews and bands.", "", "Dancing", at(-2, 16), "Field", "Student Council",
            "sample-org3", "Paolo Cruz", "", 10, 9, 0, TalentFee.FREE)
    )

    /** Ana's place in each event: applied to one, going to two (one already past). */
    val creatorStatus = mapOf("sample-bands" to "pending", "sample-sunset" to "going", "sample-fair" to "going")

    fun organized() = listOf(OrganizedEvent(acousticNight(), 3),
        OrganizedEvent(CampusEvent("sample-search", "Talent Search Live", "Open auditions.", "", "Music", at(40, 14), "Gym",
            "Indie Studio", spotter.uid, spotter.displayName, "", 10, 0, 0, TalentFee.FREE), 0))

    fun application(status: String = "pending", user: String = creator.uid, name: String = creator.displayName,
        note: String = "I sing OPM ballads with my guitar. There's a clip on my profile.", hoursAgo: Int = 2) =
        EventApplication("sample-acoustic", user, name, "", note, status, System.currentTimeMillis() - hoursAgo * 3_600_000L,
            "Acoustic Night at the Quad", at(11, 19), spotter.uid)

    fun applications() = listOf(
        application(),
        application(user = "sample-cara", name = "Cara Lim", note = "I can take photos between sets.", hoursAgo = 26),
        application(user = "sample-mico", name = "Mico Santos", note = "", hoursAgo = 30),
        application("accepted", "sample-jo", "Jo Reyes", "Two OPM songs.", 50),
        application("declined", "sample-kai", "Kai Uy", "", 60)
    )

    val applicants = mapOf(
        creator.uid to Applicant("ana_c", listOf("Singing", "Guitar")),
        "sample-cara" to Applicant("cara", listOf("Photography")),
        "sample-mico" to Applicant("mico", listOf("Guitar", "Drums")),
        "sample-jo" to Applicant("jo", listOf("Singing")),
        "sample-kai" to Applicant("kai", listOf("Dancing"))
    )
}
