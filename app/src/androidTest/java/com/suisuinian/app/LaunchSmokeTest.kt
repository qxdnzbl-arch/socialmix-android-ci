package com.suisuinian.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(SocialExperimentActivity::class.java)

    @Test
    fun freshInstall_launchesWithoutCrash_andShowsLogin() {
        onView(withText("进入你的消息和朋友")).check(matches(isDisplayed()))
    }
}
