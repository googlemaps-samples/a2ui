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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maui.data.ChatRepository
import com.example.maui.telemetry.LatencyLogger
import com.example.maui.telemetry.ResourceLogger
import com.example.maui.ui.ChatViewModel
import com.google.android.libraries.mapsplatform.a2ui.A2UIView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class A2UIViewRenderState(val messageId: String, val json: String)

private val SAMPLE_PROMPTS =
  listOf(
    "Show me 5 coffee shops near South Lake Union in Seattle",
    "Is the Edgewater Hotel in Seattle a good hotel?",
    "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?",
    "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)",
    "Give me a 3 day itinerary for a family of 3 traveling to London",
  )

class MainActivity : ComponentActivity() {

  private lateinit var latencyLogger: LatencyLogger
  private lateinit var viewModel: ChatViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    latencyLogger = LatencyLogger(this)
    val resourceLogger = ResourceLogger(this)
    val chatRepository = ChatRepository(this)

    viewModel =
      ViewModelProvider(this, ChatViewModel.provideFactory(chatRepository, resourceLogger))[
        ChatViewModel::class.java]

    com.google.android.libraries.mapsplatform.a2ui.A2UIServices.provideAPIKey(
      BuildConfig.MAPS_API_KEY
    )

    setContent { MaterialTheme { ChatScreen(viewModel, latencyLogger) } }
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, latencyLogger: LatencyLogger) {
  val messages by viewModel.uiState.collectAsStateWithLifecycle()
  var inputText by remember { mutableStateOf("") }
  var selectedAgentType by remember { mutableStateOf(AgentType.TEMPLATE) }
  var bypassCanned by remember { mutableStateOf(false) }
  var useStreaming by remember { mutableStateOf(ChatRepository.DEFAULT_USE_STREAMING) }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    MessageList(
      messages = messages,
      modifier = Modifier.weight(1f),
      latencyLogger = latencyLogger,
      viewModel = viewModel,
    )

    ControlPanel(
      onPromptSelected = { inputText = it },
      bypassCanned = bypassCanned,
      onBypassCannedChange = { bypassCanned = it },
      useStreaming = useStreaming,
      onUseStreamingChange = { useStreaming = it },
    )

    AgentSelectionColumn(
      selectedAgentType = selectedAgentType,
      onAgentTypeChange = { selectedAgentType = it },
    )

    InputBar(
      text = inputText,
      onTextChange = { inputText = it },
      onSend = {
        if (inputText.isNotBlank()) {
          viewModel.sendMessage(inputText, selectedAgentType, bypassCanned, useStreaming)
          inputText = ""
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
  onPromptSelected: (String) -> Unit,
  bypassCanned: Boolean,
  onBypassCannedChange: (Boolean) -> Unit,
  useStreaming: Boolean = ChatRepository.DEFAULT_USE_STREAMING,
  onUseStreamingChange: (Boolean) -> Unit = {},
) {
  var expanded by remember { mutableStateOf(false) }
  val defaultPrompt = stringResource(R.string.most_asked_questions)
  var selectedPrompt by remember { mutableStateOf(defaultPrompt) }
  val examplePrompts = remember(defaultPrompt) { listOf(defaultPrompt) + SAMPLE_PROMPTS }

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = it },
      modifier = Modifier.weight(1f),
    ) {
      TextField(
        value = selectedPrompt,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors =
          TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
          ),
        modifier =
          Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        examplePrompts.forEachIndexed { index, prompt ->
          DropdownMenuItem(
            text = { Text(text = prompt, color = MaterialTheme.colorScheme.onSurface) },
            onClick = {
              selectedPrompt = prompt
              expanded = false
              if (index > 0) {
                onPromptSelected(prompt)
              }
            },
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.label_server),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Switch(
        checked = bypassCanned,
        onCheckedChange = onBypassCannedChange,
        modifier = Modifier.scale(0.8f).testTag("switchCannedServer"),
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Streaming",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Switch(
        checked = useStreaming,
        onCheckedChange = onUseStreamingChange,
        modifier = Modifier.scale(0.8f).testTag("switchStreaming"),
      )
    }
  }
}

@Composable
fun AgentSelectionColumn(selectedAgentType: AgentType, onAgentTypeChange: (AgentType) -> Unit) {
  Column(
    verticalArrangement = Arrangement.spacedBy(0.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      RadioButton(
        selected = selectedAgentType == AgentType.LITE,
        onClick = { onAgentTypeChange(AgentType.LITE) },
        colors =
          RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.scale(0.8f).testTag("radioAgentLite"),
      )
      Text(
        stringResource(R.string.agent_grounding_lite),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      RadioButton(
        selected = selectedAgentType == AgentType.VERTEX,
        onClick = { onAgentTypeChange(AgentType.VERTEX) },
        colors =
          RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.scale(0.8f).testTag("radioAgentVertex"),
      )
      Text(
        stringResource(R.string.agent_grounding_vertex),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      RadioButton(
        selected = selectedAgentType == AgentType.TEMPLATE,
        onClick = { onAgentTypeChange(AgentType.TEMPLATE) },
        colors =
          RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.scale(0.8f).testTag("radioAgentTemplate"),
      )
      Text(
        stringResource(R.string.agent_template),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun MessageList(
  messages: List<ChatMessage>,
  modifier: Modifier = Modifier,
  latencyLogger: LatencyLogger,
  viewModel: ChatViewModel,
) {
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  LazyColumn(
    state = listState,
    modifier = modifier.padding(horizontal = 8.dp).testTag("recyclerView"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(items = messages, key = { it.id }) { message ->
      when (message) {
        is ChatMessage.Text -> {
          val bubbleColor =
            if (message.isUser) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            }
          val textColor =
            if (message.isUser) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart,
          ) {
            Text(
              text = message.text,
              color = textColor,
              modifier =
                Modifier.background(color = bubbleColor, shape = RoundedCornerShape(8.dp))
                  .padding(12.dp),
            )
          }
        }
        is ChatMessage.Loading -> {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is ChatMessage.GmpA2UIView -> {
          AndroidView(
            factory = { context ->
              A2UIView(context).apply {
                layoutParams =
                  android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                  )
              }
            },
            update = { view ->
              view.onRenderComplete = { latencyMs, status ->
                coroutineScope.launch {
                  latencyLogger.logLatency("A2UI", latencyMs, status)
                  delay(100)
                  if (!listState.isScrollInProgress) {
                    val currentIdx = messages.indexOfFirst { it.id == message.id }
                    if (currentIdx >= 0) {
                      val scrollTarget = if (currentIdx > 0) currentIdx - 1 else currentIdx
                      listState.animateScrollToItem(scrollTarget)
                    }
                  }
                }
              }

              view.onUserAction = { actionJson ->
                viewModel.handleAgentAction("get_directions", actionJson)
              }

              val lastState = view.tag as? A2UIViewRenderState
              if (lastState == null || lastState.messageId != message.id) {
                view.render(message.a2uiJsonString, message.startTime)
                view.tag = A2UIViewRenderState(message.id, message.a2uiJsonString)
              } else if (lastState.json != message.a2uiJsonString) {
                view.updateA2uiJson(message.a2uiJsonString)
                view.tag = A2UIViewRenderState(message.id, message.a2uiJsonString)
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("gmpA2UIView"),
          )
        }
      }
    }
  }
}

@Composable
fun InputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextField(
      value = text,
      onValueChange = onTextChange,
      modifier = Modifier.weight(1f).testTag("editTextMessage"),
      placeholder = {
        Text(
          stringResource(R.string.hint_type_message),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      colors =
        TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          disabledContainerColor = Color.Transparent,
          focusedIndicatorColor = MaterialTheme.colorScheme.primary,
          unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
          cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
    Spacer(Modifier.width(8.dp))
    Button(
      onClick = onSend,
      shape = RectangleShape,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      modifier = Modifier.testTag("buttonSend"),
    ) {
      Text(stringResource(R.string.button_send))
    }
  }
}
