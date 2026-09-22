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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.maui.A2UIWebViewAssertions.Components
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI test suite for the A2UI Android sample application. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  // ===============================================================================================
  // Core architectural UI tests
  // ===============================================================================================

  /** Verifies the WebView height dynamically expands to fit its web content. */
  @Test
  fun dynamicHeight_expandsToFitContent() {
    runTestCase(SEATTLE_COFFEE_SHOPS_PROMPT)
    A2UIWebViewAssertions.assertDynamicHeightMatchesContent(composeTestRule)
  }

  // ===============================================================================================
  // Canned response tests
  // ===============================================================================================

  /** Verifies Seattle Coffee Shops renders Map View and Place Details. */
  @Test
  fun seattleCoffeeShops_rendersMapAndPlaceDetails() {
    runAndVerifyCannedTestCase(SEATTLE_COFFEE_SHOPS_PROMPT)
  }

  /** Verifies Edgewater Hotel renders Map View and Place Details. */
  @Test
  fun edgewaterHotel_rendersMapAndPlaceDetails() {
    runAndVerifyCannedTestCase(EDGEWATER_HOTEL_PROMPT)
  }

  /** Verifies Kirkland Commute renders Map View and Place Details. */
  @Test
  fun kirklandCommute_rendersMapAndPlaceDetails() {
    runAndVerifyCannedTestCase(KIRKLAND_COMMUTE_PROMPT)
  }

  /** Verifies SLU Salads (Directions) renders Map View and Place Details. */
  @Test
  fun sluSaladsDirections_rendersMapAndPlaceDetails() {
    runAndVerifyCannedTestCase(SLU_SALADS_DIRECTIONS_PROMPT)
  }

  /** Verifies London Itinerary renders Map View and Place Details. */
  @Test
  fun londonItinerary_rendersMapAndPlaceDetails() {
    runAndVerifyCannedTestCase(LONDON_ITINERARY_PROMPT)
  }

  // ===============================================================================================
  // Helpers
  // ===============================================================================================

  /** Sends [prompt] and waits for the resulting A2UI surface to attach and lay out. */
  private fun runTestCase(prompt: String) {
    composeTestRule.onNodeWithTag(EDIT_TEXT_TAG).performTextReplacement(prompt)
    composeTestRule.onNodeWithTag(SEND_BUTTON_TAG).performClick()
    A2UIWebViewAssertions.awaitSurface(composeTestRule)
  }

  /** Verifies a canned response renders Map View and Place Details. */
  private fun runAndVerifyCannedTestCase(prompt: String) {
    runTestCase(prompt)
    A2UIWebViewAssertions.assertComponentRendered(composeTestRule, Components.MAP)
    A2UIWebViewAssertions.assertComponentRendered(composeTestRule, Components.PLACE_DETAILS)
  }

  private companion object {
    /** `testTag`s declared in `MainActivity.kt`. */
    const val EDIT_TEXT_TAG = "editTextMessage"

    const val SEND_BUTTON_TAG = "buttonSend"

    /** Prompts matching the canned responses in `//third_party/googlemaps_samples/a2ui:BUILD`. */
    const val SEATTLE_COFFEE_SHOPS_PROMPT =
      "Show me 5 coffee shops near South Lake Union in Seattle"

    const val EDGEWATER_HOTEL_PROMPT = "Is the Edgewater Hotel in Seattle a good hotel?"

    const val KIRKLAND_COMMUTE_PROMPT =
      "How long will it take to commute to Google Kirkland office from downtown Redmond during " +
        "my morning rush hour commute?"

    const val SLU_SALADS_DIRECTIONS_PROMPT =
      "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the " +
        "2nd one (starting from the Google South Lake Union WLK building)"

    const val LONDON_ITINERARY_PROMPT =
      "Give me a 3 day itinerary for a family of 3 traveling to London"
  }
}
