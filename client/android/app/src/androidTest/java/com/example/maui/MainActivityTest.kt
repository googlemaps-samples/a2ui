//
// Copyright 2026 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.example.maui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

  @Test
  fun testSeattleCoffeeShopsCannedResponse() {
    onView(withId(R.id.editTextMessage))
      .perform(
        replaceText("Show me 5 coffee shops near South Lake Union in Seattle"),
        closeSoftKeyboard(),
      )
    onView(withId(R.id.buttonSend)).perform(click())
    Thread.sleep(7000)

    // Verify that the A2UIView container (which renders the mock JSON) is displayed
    onView(withId(R.id.gmpA2UIView)).check(matches(isDisplayed()))

    // Scroll the recycler view to see the full content
    onView(withId(R.id.recyclerView)).perform(swipeUp())

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testEdgewaterHotelCannedResponse() {
    onView(withId(R.id.editTextMessage))
      .perform(replaceText("Is the Edgewater Hotel in Seattle a good hotel?"), closeSoftKeyboard())
    onView(withId(R.id.buttonSend)).perform(click())
    Thread.sleep(7000)

    // Verify that the A2UIView container (which renders the mock JSON) is displayed
    onView(withId(R.id.gmpA2UIView)).check(matches(isDisplayed()))

    // Scroll the recycler view to see the full content
    onView(withId(R.id.recyclerView)).perform(swipeUp())

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testKirklandCommuteCannedResponse() {
    onView(withId(R.id.editTextMessage))
      .perform(
        replaceText(
          "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?"
        ),
        closeSoftKeyboard(),
      )
    onView(withId(R.id.buttonSend)).perform(click())
    Thread.sleep(7000)

    // Verify that the A2UIView container (which renders the mock JSON) is displayed
    onView(withId(R.id.gmpA2UIView)).check(matches(isDisplayed()))

    // Scroll the recycler view to see the full content
    onView(withId(R.id.recyclerView)).perform(swipeUp())

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testSLUSaladsCannedResponse() {
    onView(withId(R.id.editTextMessage))
      .perform(
        replaceText(
          "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)"
        ),
        closeSoftKeyboard(),
      )
    onView(withId(R.id.buttonSend)).perform(click())
    Thread.sleep(7000)

    // Verify that the A2UIView container (which renders the mock JSON) is displayed
    onView(withId(R.id.gmpA2UIView)).check(matches(isDisplayed()))

    // Scroll the recycler view to see the full content
    onView(withId(R.id.recyclerView)).perform(swipeUp())

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testLondonItineraryCannedResponse() {
    onView(withId(R.id.editTextMessage))
      .perform(
        replaceText("Give me a 3 day itinerary for a family of 3 traveling to London"),
        closeSoftKeyboard(),
      )
    onView(withId(R.id.buttonSend)).perform(click())
    Thread.sleep(7000)

    // Verify that the A2UIView container (which renders the mock JSON) is displayed
    onView(withId(R.id.gmpA2UIView)).check(matches(isDisplayed()))

    // Scroll the recycler view to see the full content
    onView(withId(R.id.recyclerView)).perform(swipeUp())

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }
}
