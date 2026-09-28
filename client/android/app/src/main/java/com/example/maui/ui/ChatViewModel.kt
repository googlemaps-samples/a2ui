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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.maui.AgentType
import com.example.maui.ChatMessage
import com.example.maui.data.ChatRepository
import com.example.maui.telemetry.ResourceLogger
import com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent
import java.util.UUID
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
  private var currentStreamingMessageId: String? = null
  private var currentUpdateComponentsMsg: JSONObject? = null
  private val currentUpdateDataModelMsgs = mutableListOf<JSONObject>()
  private val currentStreamingText = StringBuilder()
  private var currentStreamingTextId: String? = null
  private var lastUseStreaming: Boolean = ChatRepository.DEFAULT_USE_STREAMING

  init {
    resourceLogger.startLogging(viewModelScope)
  }

  private fun resetStreamingStateAndCancelJob() {
    currentRequestJob?.cancel()
    currentStreamingMessageId = null
    currentUpdateComponentsMsg = null
    currentUpdateDataModelMsgs.clear()
    currentStreamingText.clear()
    currentStreamingTextId = null
  }

  fun sendMessage(
    text: String,
    agentType: AgentType = AgentType.LITE,
    bypassCanned: Boolean = false,
    useStreaming: Boolean = ChatRepository.DEFAULT_USE_STREAMING,
  ) {
    resetStreamingStateAndCancelJob()
    lastUseStreaming = useStreaming
    val serverMessageText =
      when (agentType) {
        AgentType.VERTEX -> "[GROUNDING] $text"
        AgentType.TEMPLATE -> "[TEMPLATE] $text"
        AgentType.LITE -> text
      }

    addMessage(ChatMessage.Text(text = text, isUser = true))
    val jsonObject =
      JSONObject().apply {
        put("text", serverMessageText)
        put("bypassCanned", bypassCanned)
        put("useStreaming", useStreaming)
      }
    sendPayload(jsonObject)
  }

  fun handleAgentAction(actionName: String, contextJson: String) {
    resetStreamingStateAndCancelJob()
    val userAction =
      JSONObject().apply {
        put("name", actionName)
        put("context", JSONObject(contextJson))
      }
    val jsonObject =
      JSONObject().apply {
        put("userAction", userAction)
        put("useStreaming", lastUseStreaming)
      }
    sendPayload(jsonObject)
  }

  private fun sendPayload(jsonObject: JSONObject) {
    addMessage(ChatMessage.Loading)
    currentRequestJob = viewModelScope.launch {
      try {
        repository.callPythonServer(jsonObject).collect { result ->
          result
            .onSuccess { parsedEvents -> handleParsedEvents(parsedEvents) }
            .onFailure { exception ->
              removeLastLoadingMessage()
              addMessage(
                ChatMessage.Text(text = exception.message ?: "Unknown Error", isUser = false)
              )
            }
        }
      } finally {
        removeLastLoadingMessage()
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

  private fun handleParsedEvents(events: List<ParsedA2AEvent>) {
    var foundRealContent = false
    var updatedTextMessage: ChatMessage.Text? = null
    var updatedMapSpec: Pair<String, String>? = null
    val deletedMapMessageIds = mutableSetOf<String>()
    val fallbackStartTime = System.currentTimeMillis()

    for (part in events) {
      when (part) {
        is ParsedA2AEvent.Text -> {
          if (part.text.isNotEmpty()) {
            foundRealContent = true
            currentStreamingText.append(part.text)
            val fullText = currentStreamingText.toString()
            if (currentStreamingTextId == null) {
              currentStreamingTextId = UUID.randomUUID().toString()
            }
            updatedTextMessage =
              ChatMessage.Text(text = fullText, isUser = false, id = currentStreamingTextId!!)
          }
        }
        is ParsedA2AEvent.Data -> {
          val jsonString = part.data
          if (jsonString.contains(DUMMY_STATUS_TOKEN)) {
            continue
          }
          foundRealContent = true

          try {
            val incomingArray =
              if (jsonString.trim().startsWith("[")) {
                JSONArray(jsonString)
              } else {
                JSONArray().put(JSONObject(jsonString))
              }
            for (i in 0 until incomingArray.length()) {
              val a2uiMessage = incomingArray.optJSONObject(i) ?: continue
              when {
                a2uiMessage.has(KEY_DELETE_SURFACE) -> {
                  currentStreamingMessageId?.let { deletedMapMessageIds.add(it) }
                  currentStreamingMessageId = null
                  currentUpdateComponentsMsg = null
                  currentUpdateDataModelMsgs.clear()
                  updatedMapSpec = null
                }
                a2uiMessage.has(KEY_CREATE_SURFACE) -> {
                  // Omit createSurface so core-shell.ts auto-injects it at index 0
                  // only when the surface does not exist yet.
                }
                a2uiMessage.has(KEY_UPDATE_COMPONENTS) -> {
                  val componentsArray =
                    a2uiMessage.optJSONObject(KEY_UPDATE_COMPONENTS)?.optJSONArray(KEY_COMPONENTS)
                  if (componentsArray != null) {
                    for (j in 0 until componentsArray.length()) {
                      val componentJson = componentsArray.optJSONObject(j) ?: continue
                      if (componentJson.optString(KEY_COMPONENT) == COMPONENT_GOOGLE_MAP) {
                        if (componentJson.opt(KEY_MARKERS) is JSONObject) {
                          componentJson.put(KEY_MARKERS, JSONArray())
                        }
                        if (componentJson.opt(KEY_ROUTES) is JSONObject) {
                          componentJson.put(KEY_ROUTES, JSONArray())
                        }
                      }
                    }
                  }
                  currentUpdateComponentsMsg = a2uiMessage
                }
                a2uiMessage.has(KEY_UPDATE_DATA_MODEL) -> {
                  currentUpdateDataModelMsgs.add(a2uiMessage)
                }
              }
            }
          } catch (e: Exception) {
            // Fallback if jsonString is non-standard
          }

          val componentsMsg = currentUpdateComponentsMsg ?: continue
          val outArray = JSONArray()
          outArray.put(componentsMsg)
          for (dataModelMessage in currentUpdateDataModelMsgs) {
            outArray.put(dataModelMessage)
          }
          val accumulatedJson = outArray.toString()

          if (currentStreamingMessageId == null) {
            currentStreamingMessageId = UUID.randomUUID().toString()
          }
          updatedMapSpec = Pair(currentStreamingMessageId!!, accumulatedJson)
        }
      }
    }

    _uiState.update { currentList ->
      val mutableList = currentList.toMutableList()

      if (
        foundRealContent && mutableList.isNotEmpty() && mutableList.last() is ChatMessage.Loading
      ) {
        mutableList.removeAt(mutableList.size - 1)
      }

      if (deletedMapMessageIds.isNotEmpty()) {
        mutableList.removeAll { (it as? ChatMessage.GmpA2UIView)?.id in deletedMapMessageIds }
      }

      if (updatedTextMessage != null) {
        val targetTextIndex = mutableList.indexOfFirst {
          it is ChatMessage.Text && it.id == updatedTextMessage.id
        }
        if (targetTextIndex != -1) {
          mutableList[targetTextIndex] = updatedTextMessage
        } else {
          mutableList.add(updatedTextMessage)
        }
      }

      if (updatedMapSpec != null) {
        val (messageId, accumulatedJson) = updatedMapSpec
        val targetIndex = mutableList.indexOfFirst {
          (it as? ChatMessage.GmpA2UIView)?.id == messageId
        }
        val existingStartTime =
          if (targetIndex != -1) {
            (mutableList[targetIndex] as? ChatMessage.GmpA2UIView)?.startTime
          } else null

        val newMessage =
          ChatMessage.GmpA2UIView(
            a2uiJsonString = accumulatedJson,
            startTime = existingStartTime ?: fallbackStartTime,
            id = messageId,
          )

        if (targetIndex != -1) {
          mutableList[targetIndex] = newMessage
        } else {
          mutableList.add(newMessage)
        }
      }

      mutableList.toList()
    }
  }

  override fun onCleared() {
    resourceLogger.stopLogging()
    super.onCleared()
  }

  companion object {
    private const val DUMMY_STATUS_TOKEN = "dummy_status"
    private const val KEY_DELETE_SURFACE = "deleteSurface"
    private const val KEY_CREATE_SURFACE = "createSurface"
    private const val KEY_UPDATE_COMPONENTS = "updateComponents"
    private const val KEY_UPDATE_DATA_MODEL = "updateDataModel"
    private const val KEY_COMPONENTS = "components"
    private const val KEY_COMPONENT = "component"
    private const val COMPONENT_GOOGLE_MAP = "GoogleMap"
    private const val KEY_MARKERS = "markers"
    private const val KEY_ROUTES = "routes"

    fun provideFactory(
      repository: ChatRepository,
      resourceLogger: ResourceLogger,
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return ChatViewModel(repository, resourceLogger) as T
        }
      }
  }
}
