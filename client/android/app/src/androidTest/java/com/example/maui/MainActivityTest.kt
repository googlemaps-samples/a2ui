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
import com.example.maui.A2UIWebViewAssertions.Components
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI test suite for the A2UI Android sample application. */
@RunWith(TestParameterInjector::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun dynamicHeight_expandsToFitContent() {
    sendPromptAndAwaitSurface(CannedResponse.SEATTLE_COFFEE_SHOPS.prompt)

    A2UIWebViewAssertions.assertDynamicHeightMatchesContent(composeTestRule)
  }

  @Test
  fun cannedResponse_rendersMapAndPlaceDetails(@TestParameter cannedResponse: CannedResponse) {
    sendPromptAndAwaitSurface(cannedResponse.prompt)

    A2UIWebViewAssertions.assertComponentRendered(composeTestRule, Components.MAP)
    A2UIWebViewAssertions.assertComponentRendered(composeTestRule, Components.PLACE_DETAILS)
  }

  /** Prompts matching the canned responses in `assets/canned_responses/mapping.json`. */
  enum class CannedResponse(val prompt: String) {
    SEATTLE_COFFEE_SHOPS("Show me 5 coffee shops near South Lake Union in Seattle"),
    EDGEWATER_HOTEL("Is the Edgewater Hotel in Seattle a good hotel?"),
    KIRKLAND_COMMUTE(
      "How long will it take to commute to Google Kirkland office from downtown Redmond during " +
        "my morning rush hour commute?"
    ),
    SLU_SALADS_DIRECTIONS(
      "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the " +
        "2nd one (starting from the Google South Lake Union WLK building)"
    ),
    LONDON_ITINERARY("Give me a 3 day itinerary for a family of 3 traveling to London"),
  }

  /** Sends [prompt] and waits for the resulting A2UI surface to attach and lay out. */
  private fun sendPromptAndAwaitSurface(prompt: String) {
    composeTestRule.onNodeWithTag(EDIT_TEXT_TAG).performTextReplacement(prompt)
    composeTestRule.onNodeWithTag(SEND_BUTTON_TAG).performClick()
    A2UIWebViewAssertions.awaitSurface(composeTestRule)
  }

  companion object {
    /** `testTag`s declared in `MainActivity.kt`. */
    private const val EDIT_TEXT_TAG = "editTextMessage"

    private const val SEND_BUTTON_TAG = "buttonSend"
  }
}
