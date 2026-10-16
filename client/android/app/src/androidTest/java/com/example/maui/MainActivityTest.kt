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

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun testSeattleCoffeeShopsCannedResponse() {
    composeTestRule
      .onNodeWithTag("editTextMessage")
      .performTextReplacement("Show me 5 coffee shops near South Lake Union in Seattle")
    composeTestRule.onNodeWithTag("buttonSend").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithTag("gmpA2UIView").fetchSemanticsNodes().isNotEmpty()
    }

    // Verify that the A2UIView container (which renders the mock JSON) exists
    composeTestRule.onNodeWithTag("gmpA2UIView").assertExists()

    // Scroll the message list to see the full content
    composeTestRule.onNodeWithTag("recyclerView").performScrollToIndex(1)

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testEdgewaterHotelCannedResponse() {
    composeTestRule
      .onNodeWithTag("editTextMessage")
      .performTextReplacement("Is the Edgewater Hotel in Seattle a good hotel?")
    composeTestRule.onNodeWithTag("buttonSend").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithTag("gmpA2UIView").fetchSemanticsNodes().isNotEmpty()
    }

    // Verify that the A2UIView container (which renders the mock JSON) exists
    composeTestRule.onNodeWithTag("gmpA2UIView").assertExists()

    // Scroll the message list to see the full content
    composeTestRule.onNodeWithTag("recyclerView").performScrollToIndex(1)

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testKirklandCommuteCannedResponse() {
    composeTestRule
      .onNodeWithTag("editTextMessage")
      .performTextReplacement(
        "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?"
      )
    composeTestRule.onNodeWithTag("buttonSend").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithTag("gmpA2UIView").fetchSemanticsNodes().isNotEmpty()
    }

    // Verify that the A2UIView container (which renders the mock JSON) exists
    composeTestRule.onNodeWithTag("gmpA2UIView").assertExists()

    // Scroll the message list to see the full content
    composeTestRule.onNodeWithTag("recyclerView").performScrollToIndex(1)

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testSLUSaladsCannedResponse() {
    composeTestRule
      .onNodeWithTag("editTextMessage")
      .performTextReplacement(
        "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)"
      )
    composeTestRule.onNodeWithTag("buttonSend").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithTag("gmpA2UIView").fetchSemanticsNodes().isNotEmpty()
    }

    // Verify that the A2UIView container (which renders the mock JSON) exists
    composeTestRule.onNodeWithTag("gmpA2UIView").assertExists()

    // Scroll the message list to see the full content
    composeTestRule.onNodeWithTag("recyclerView").performScrollToIndex(1)

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }

  @Test
  fun testLondonItineraryCannedResponse() {
    composeTestRule
      .onNodeWithTag("editTextMessage")
      .performTextReplacement("Give me a 3 day itinerary for a family of 3 traveling to London")
    composeTestRule.onNodeWithTag("buttonSend").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithTag("gmpA2UIView").fetchSemanticsNodes().isNotEmpty()
    }

    // Verify that the A2UIView container (which renders the mock JSON) exists
    composeTestRule.onNodeWithTag("gmpA2UIView").assertExists()

    // Scroll the message list to see the full content
    composeTestRule.onNodeWithTag("recyclerView").performScrollToIndex(1)

    // Pause to let the observer see the final state before the next test starts
    Thread.sleep(2000)
  }
}
