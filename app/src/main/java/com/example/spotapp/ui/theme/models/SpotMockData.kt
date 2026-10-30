package com.example.spotapp.ui.theme.models

import com.example.spotapp.R

/** Sample people and a sample post for the on-phone screen tests (HomeExperienceTest, MessagesExperienceTest, ChatOptionsReviewTest). */
object SpotMockData {

    val mika = SpotUiProfile(
        uid = "mika",
        displayName = "Mika Suarez",
        username = "mikasuarez",
        talent = "Dancer",
        course = "Bachelor of Science in Computer Science"
    )

    val deandra = SpotUiProfile(
        uid = "deandra",
        displayName = "Deandra Pasquin",
        username = "deandrapasquin",
        talent = "Dancer",
        course = "Bachelor of Science in Computer Science"
    )

    fun homePostFor(
        user: SpotUiProfile
    ): SpotUiPost {

        return SpotUiPost(
            id = "home-sample",
            user = user,

            caption = "A new talent moment I’ve been working on this week. Still improving it, but I’m happy with the progress so far.",

            category = user.talent.ifBlank {
                "Talent"
            },

            tags = listOf(
                "SPOT",
                "CampusTalent"
            ),
            postedTime = "1 min ago",

            localImageResources = listOf(
                R.drawable.image1,
                R.drawable.image2,
                R.drawable.image3
            ),

            likes = 2000,
            comments = 1000,
            shares = 100,

            aiCaption = true,
            aiTagged = true
        )
    }
}
