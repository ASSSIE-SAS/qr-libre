package co.asssie.qrlibre.ui

import co.asssie.qrlibre.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacySectionsTest {

    @Test
    fun localReassuranceComesFirstAndNoSectionIsDropped() {
        val expectedTitles = listOf(
            R.string.privacy_stays_title,
            R.string.privacy_reads_title,
            R.string.privacy_limit_title,
            R.string.privacy_delete_title,
            R.string.privacy_identity_title,
        )

        assertEquals(
            "privacy must lead with where the data stays and keep all five disclosures",
            expectedTitles,
            privacySections.map { it.title },
        )
    }

    @Test
    fun eachSectionKeepsItsOwnBody() {
        val expectedBodies = mapOf(
            R.string.privacy_stays_title to R.string.privacy_stays_body,
            R.string.privacy_reads_title to R.string.privacy_reads_body,
            R.string.privacy_limit_title to R.string.privacy_limit_body,
            R.string.privacy_delete_title to R.string.privacy_delete_body,
            R.string.privacy_identity_title to R.string.privacy_identity_body,
        )

        assertEquals("exactly five disclosures are rendered", 5, privacySections.size)
        privacySections.forEach { section ->
            assertEquals(
                "section ${section.title} must keep its own body",
                expectedBodies.getValue(section.title),
                section.body,
            )
        }
    }
}
