package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ProfileDetailsTest {
    private fun student() = ProfileDetails(AccountType.STUDENT, "Alex", "alex_01", "BS IT", listOf("Photography"))
    private fun seeker() = ProfileDetails(AccountType.TALENT_SEEKER, "Alex", "alex_01")

    @Test fun seekerCanFinishWithoutStudentOrOptionalFields() {
        val profile = seeker()
        assertNull(profile.validationError())
        assertEquals("talent_seeker", profile.firestoreFields()["accountType"])
        assertEquals("", profile.firestoreFields()["course"])
        assertEquals(emptyList<String>(), profile.firestoreFields()["talentCategories"])
    }

    @Test fun studentRequiresCourseAndAtLeastOneTalent() {
        assertNotNull(student().copy(course = " ").validationError())
        assertNotNull(student().copy(talents = listOf(" ")).validationError())
        assertNull(student().validationError())
    }

    @Test fun switchingRolesDoesNotPublishFieldsFromOtherRole() {
        val switched = student().copy(accountType = AccountType.TALENT_SEEKER,
            organization = " Studio ", seekingTalents = listOf(" Guitar ", "Guitar", ""))
        val fields = switched.firestoreFields()
        assertEquals("", fields["course"])
        assertEquals(emptyList<String>(), fields["talentCategories"])
        assertEquals(listOf("Guitar"), fields["seekingTalents"])
        assertEquals("Studio", fields["organization"])
        val returned = switched.copy(accountType = AccountType.STUDENT).firestoreFields()
        assertEquals("", returned["organization"])
        assertEquals(emptyList<String>(), returned["seekingTalents"])
        assertEquals(listOf("Photography"), returned["talentCategories"])
    }

    @Test fun usernameNormalizationIsIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val profile = seeker().copy(username = " INDIE_STUDIO ")
            assertNull(profile.validationError())
            assertEquals("indie_studio", profile.firestoreFields()["username"])
        } finally { Locale.setDefault(original) }
    }

    @Test fun invalidUsernamesAndOversizedBioAreRejected() {
        for (name in listOf("ab", "name with spaces", "not@valid", "a".repeat(31))) {
            assertNotNull(seeker().copy(username = name).validationError())
        }
        assertNotNull(seeker().copy(bio = "x".repeat(161)).validationError())
    }

    @Test fun legacyAndUnknownRolesDoNotAcquireAStatus() {
        assertNull(AccountType.fromStorage(null))
        assertNull(AccountType.fromStorage("admin"))
        assertEquals(AccountType.STUDENT, AccountType.fromStorage("student"))
        assertEquals(AccountType.TALENT_SEEKER, AccountType.fromStorage("talent_seeker"))
        assertFalse(seeker().firestoreFields().containsKey("isVerifiedTalent"))
    }
}
