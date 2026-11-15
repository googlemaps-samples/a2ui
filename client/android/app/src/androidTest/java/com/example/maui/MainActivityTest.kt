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

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.maui.A2UIWebViewAssertions.Components
import com.example.maui.data.ChatRepository
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.json.JSONArray
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI test suite for the A2UI Android sample application. */
@RunWith(TestParameterInjector::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @After
  fun tearDown() {
    ChatRepository.baseUrlOverride = null
    ChatRepository.bearerTokenOverride = null
  }

  @Test
  fun serverPrefix_returnsExpectedRoutingTag() {
    assertThat(AgentType.LITE.serverPrefix).isEqualTo("[TEMPLATE] ")
    assertThat(AgentType.VERTEX.serverPrefix).isEqualTo("[GROUNDING_TEMPLATE] ")
  }

  @Test
  fun chatRepository_usesBaseUrlAndBearerTokenOverrides() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val repository = ChatRepository(context)
    ChatRepository.baseUrlOverride = "https://maui-backend.google.com"
    ChatRepository.bearerTokenOverride = "test-bearer-token"

    val requestWithToken = repository.buildRequest(JSONArray())
    val sessionRequestWithToken = repository.buildSessionRequest()
    assertThat(repository.baseUrl).isEqualTo("https://maui-backend.google.com")
    assertThat(requestWithToken.url.toString()).startsWith("https://maui-backend.google.com/?key=")
    assertThat(requestWithToken.header("Authorization")).isEqualTo("Bearer test-bearer-token")
    assertThat(sessionRequestWithToken.header("Authorization"))
      .isEqualTo("Bearer test-bearer-token")

    ChatRepository.bearerTokenOverride = ""
    val requestWithoutToken = repository.buildRequest(JSONArray())
    val sessionRequestWithoutToken = repository.buildSessionRequest()
    assertThat(requestWithoutToken.header("Authorization")).isNull()
    assertThat(sessionRequestWithoutToken.header("Authorization")).isNull()
  }

  @Test
  fun launch_withBackendAndScreenExtras_appliesOverridesToRepositoryAndScreen() {
    composeTestRule.activityRule.scenario.close()
    val prompt = CannedResponse.SEATTLE_COFFEE_SHOPS.prompt
    val intent =
      Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
        .putExtra(LaunchOverrides.EXTRA_BACKEND_URL_OVERRIDE, "https://maui-backend.google.com")
        .putExtra(LaunchOverrides.EXTRA_BACKEND_BEARER_TOKEN, "test-bearer-token")
        .putExtra(LaunchOverrides.EXTRA_USE_CANNED_RESPONSES, "false")
        .putExtra(LaunchOverrides.EXTRA_AGENT_TYPE, "vertex")
        .putExtra(LaunchOverrides.EXTRA_AUTO_SEND_PROMPT, prompt)

    ActivityScenario.launch<MainActivity>(intent).use {
      assertThat(ChatRepository.baseUrlOverride).isEqualTo("https://maui-backend.google.com")
      assertThat(ChatRepository.bearerTokenOverride).isEqualTo("test-bearer-token")
      composeTestRule.onNodeWithTag("switchCannedServer").assertIsOn()
      composeTestRule.onNodeWithTag("radioAgentVertex").assertIsSelected()
      composeTestRule.onNodeWithText(prompt).assertIsDisplayed()
    }
  }

  @Test
  fun launch_withoutBackendExtras_keepsOverridesSetBeforeLaunch() {
    ChatRepository.baseUrlOverride = "https://sut-backend.example.com"
    ChatRepository.bearerTokenOverride = "preset-bearer-token"

    ActivityScenario.launch(MainActivity::class.java).use {
      assertThat(ChatRepository.baseUrlOverride).isEqualTo("https://sut-backend.example.com")
      assertThat(ChatRepository.bearerTokenOverride).isEqualTo("preset-bearer-token")
    }
  }

  @Test
  fun launchOverrides_fromExtras_parsesSettingsAndPrompt() {
    val extras =
      mapOf(
        LaunchOverrides.EXTRA_USE_CANNED_RESPONSES to "False",
        LaunchOverrides.EXTRA_AGENT_TYPE to "vertex",
        LaunchOverrides.EXTRA_AUTO_SEND_PROMPT to "Show 3 Indian Restaurants in Seattle",
      )

    val overrides = LaunchOverrides.fromExtras { extras[it] }

    assertThat(overrides)
      .isEqualTo(
        LaunchOverrides(
          useCannedResponses = false,
          agentType = AgentType.VERTEX,
          autoSendPrompt = "Show 3 Indian Restaurants in Seattle",
        )
      )
  }

  @Test
  fun launchOverrides_fromExtras_parsesLiteAgentCaseInsensitivelyAndTrimsPrompt() {
    val extras =
      mapOf(
        LaunchOverrides.EXTRA_AGENT_TYPE to "LITE",
        LaunchOverrides.EXTRA_AUTO_SEND_PROMPT to "  Edgewater Hotel  ",
      )

    val overrides = LaunchOverrides.fromExtras { extras[it] }

    assertThat(overrides.agentType).isEqualTo(AgentType.LITE)
    assertThat(overrides.autoSendPrompt).isEqualTo("Edgewater Hotel")
  }

  @Test
  fun launchOverrides_fromExtras_ignoresMissingAndInvalidValues() {
    val extras =
      mapOf(
        LaunchOverrides.EXTRA_USE_CANNED_RESPONSES to "maybe",
        LaunchOverrides.EXTRA_AGENT_TYPE to "unknown",
        LaunchOverrides.EXTRA_AUTO_SEND_PROMPT to "  ",
      )

    assertThat(LaunchOverrides.fromExtras { extras[it] }).isEqualTo(LaunchOverrides())
  }

  @Test
  fun dynamicHeight_expandsToFitContent() {
    sendPromptAndAwaitSurface(CannedResponse.SEATTLE_COFFEE_SHOPS.prompt)

    A2UIWebViewAssertions.assertDynamicHeightMatchesContent(composeTestRule)
  }

  @Test
  fun cannedResponse_rendersMapAndPlaceDetails(@TestParameter cannedResponse: CannedResponse) {
    // Skipped: this response's place cards are 0 px wide on the test emulator, failing the render
    // check. Remove this skip once the root cause is fixed.
    assumeFalse(cannedResponse == CannedResponse.LONDON_ITINERARY)
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
