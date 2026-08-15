//
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
//

package com.example.maui.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maui.ChatMessage
import com.example.maui.data.ChatRepository
import com.example.maui.telemetry.ResourceLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel(
  private val repository: ChatRepository,
  private val resourceLogger: ResourceLogger,
) : ViewModel() {

  private val _uiState = MutableStateFlow<List<ChatMessage>>(emptyList())
  val uiState: StateFlow<List<ChatMessage>> = _uiState.asStateFlow()

  private var currentRequestJob: Job? = null
  private var currentAgentTextIndex: Int? = null
  private var currentAgentA2UIIndex: Int? = null

  init {
    resourceLogger.startLogging(viewModelScope)
  }

  fun sendMessage(text: String, isGrounding: Boolean = false, bypassCanned: Boolean = false) {
    currentRequestJob?.cancel()
    currentAgentTextIndex = null
    currentAgentA2UIIndex = null
    val serverMessageText = if (isGrounding) "[GROUNDING] $text" else text
    addMessage(ChatMessage.Text(text, true))
    val jsonObject =
      JSONObject().apply {
        put("text", serverMessageText)
        put("bypassCanned", bypassCanned)
      }
    sendPayload(jsonObject)
  }

  fun handleAgentAction(actionName: String, contextJson: String) {
    val userAction =
      JSONObject().apply {
        put("name", actionName)
        put("context", JSONObject(contextJson))
      }
    val jsonObject = JSONObject().apply { put("userAction", userAction) }
    sendPayload(jsonObject)
  }

  private fun sendPayload(jsonObject: JSONObject) {
    addMessage(ChatMessage.Loading)
    currentRequestJob = viewModelScope.launch {
      repository.callPythonServer(jsonObject).collect { result ->
        result
          .onSuccess { agentResponse ->
            removeLastLoadingMessage()
            if (agentResponse.isCanned) {
              processJsonResponse(JSONObject(agentResponse.a2uiJson))
            } else {
              if (agentResponse.conversationalText.isNotEmpty()) {
                val textUpdate =
                  JSONObject()
                    .put(
                      "parts",
                      JSONArray().put(JSONObject().put("text", agentResponse.conversationalText)),
                    )
                processJsonResponse(textUpdate)
              }
              if (agentResponse.a2uiJson.isNotEmpty()) {
                processJsonResponse(JSONObject(agentResponse.a2uiJson))
              }
            }
          }
          .onFailure { exception ->
            removeLastLoadingMessage()
            addMessage(ChatMessage.Text(exception.message ?: "Unknown Error", false))
          }
      }
    }
  }

  private fun addMessage(message: ChatMessage) {
    _uiState.update { currentList -> currentList + message }
  }

  private fun removeLastLoadingMessage() {
    _uiState.update { currentList ->
      if (currentList.isNotEmpty() && currentList.last() is ChatMessage.Loading) {
        currentList.dropLast(1)
      } else {
        currentList
      }
    }
  }

  private fun processJsonResponse(json: JSONObject) {
    if (json.has("error")) {
      val error = json.opt("error")
      val errorMsg =
        if (error is JSONObject) error.optString("message")
        else error?.toString() ?: "Unknown error"
      addMessage(ChatMessage.Text("Server Error: $errorMsg", false))
      return
    }

    val parsedParts =
      try {
        com.google.android.libraries.mapsplatform.a2ui.A2AResponseParser.parse(json)
      } catch (e: Exception) {
        emptyList<com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent>()
      }

    var aggregatedText = StringBuilder()
    var aggregatedJson = JSONArray()

    for (part in parsedParts) {
      when (part) {
        is com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent.Text -> {
          if (aggregatedText.isNotEmpty()) aggregatedText.append("\n")
          aggregatedText.append(part.text)
        }
        is com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent.Data -> {
          if (part.data != "[]") {
            try {
              val array = JSONArray(part.data)
              for (j in 0 until array.length()) {
                aggregatedJson.put(array.get(j))
              }
            } catch (e: Exception) {}
          }
        }
      }
    }

    val finalConversationalText = aggregatedText.toString()
    val finalA2uiJson = if (aggregatedJson.length() > 0) aggregatedJson.toString() else ""

    if (finalConversationalText.isNotEmpty() || finalA2uiJson.isNotEmpty()) {
      _uiState.update { currentList ->
        val mutableList = currentList.toMutableList()
        if (finalConversationalText.isNotEmpty()) {
          currentAgentTextIndex?.let { idx ->
            if (idx < mutableList.size) {
              mutableList[idx] = ChatMessage.Text(finalConversationalText, false)
            }
          }
            ?: run {
              mutableList.add(ChatMessage.Text(finalConversationalText, false))
              currentAgentTextIndex = mutableList.size - 1
            }
        }
        if (finalA2uiJson.isNotEmpty() && finalA2uiJson != "[]") {
          currentAgentA2UIIndex?.let { idx ->
            if (idx < mutableList.size) {
              val oldMsg = mutableList[idx] as? ChatMessage.GmpA2UIView
              mutableList[idx] =
                ChatMessage.GmpA2UIView(
                  finalA2uiJson,
                  oldMsg?.startTime ?: System.currentTimeMillis(),
                )
            }
          }
            ?: run {
              val gmpViewStartTime = System.currentTimeMillis()
              mutableList.add(ChatMessage.GmpA2UIView(finalA2uiJson, gmpViewStartTime))
              currentAgentA2UIIndex = mutableList.size - 1
            }
        }
        mutableList.toList()
      }
    }
  }

  override fun onCleared() {
    resourceLogger.stopLogging()
    super.onCleared()
  }

  companion object {
    fun provideFactory(
      repository: ChatRepository,
      resourceLogger: ResourceLogger,
    ): androidx.lifecycle.ViewModelProvider.Factory =
      object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return ChatViewModel(repository, resourceLogger) as T
        }
      }
  }
}
