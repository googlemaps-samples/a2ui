// Copyright 2026 Google LLC
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

package com.example.maui

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.example.maui.data.ChatRepository

/**
 * Debug-only launch settings for automated UI tests. They let a test open the app in a given mode
 * and send a prompt without tapping the controls. They are not needed to use the SDK, and release
 * builds ignore them. A null field keeps the app's normal default.
 */
internal data class LaunchOverrides(
  val useCannedResponses: Boolean? = null,
  val agentType: AgentType? = null,
  val autoSendPrompt: String? = null,
) {
  companion object {
    const val EXTRA_BACKEND_URL_OVERRIDE = "A2UI_BACKEND_URL_OVERRIDE"
    const val EXTRA_BACKEND_BEARER_TOKEN = "A2UI_BACKEND_BEARER_TOKEN"
    const val EXTRA_USE_CANNED_RESPONSES = "A2UI_USE_CANNED_RESPONSES"
    const val EXTRA_AGENT_TYPE = "A2UI_AGENT_TYPE"
    const val EXTRA_AUTO_SEND_PROMPT = "A2UI_AUTO_SEND_PROMPT"

    /**
     * Reads launch settings from the intent of [activity] in debuggable builds, and returns no
     * overrides otherwise.
     *
     * Also applies the backend URL and Bearer token to [ChatRepository], but only when their extras
     * are passed, so an override that a test set before launching the activity is kept.
     */
    fun applyDebugIntent(activity: Activity): LaunchOverrides {
      val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
      val intent = activity.intent
      if (!isDebuggable || intent == null) {
        return LaunchOverrides()
      }
      intent
        .getStringExtra(EXTRA_BACKEND_URL_OVERRIDE)
        ?.takeIf { it.isNotEmpty() }
        ?.let { ChatRepository.baseUrlOverride = it }
      intent
        .getStringExtra(EXTRA_BACKEND_BEARER_TOKEN)
        ?.takeIf { it.isNotEmpty() }
        ?.let { ChatRepository.bearerTokenOverride = it }
      return fromExtras { key -> intent.getStringExtra(key) }
    }

    /** Parses launch settings; `getExtra` returns the string extra for a key, or null. */
    fun fromExtras(getExtra: (String) -> String?): LaunchOverrides =
      LaunchOverrides(
        useCannedResponses =
          getExtra(EXTRA_USE_CANNED_RESPONSES)?.lowercase()?.toBooleanStrictOrNull(),
        agentType =
          when (getExtra(EXTRA_AGENT_TYPE)?.lowercase()) {
            "lite" -> AgentType.LITE
            "vertex" -> AgentType.VERTEX
            else -> null
          },
        autoSendPrompt = getExtra(EXTRA_AUTO_SEND_PROMPT)?.trim()?.takeIf { it.isNotEmpty() },
      )
  }
}
