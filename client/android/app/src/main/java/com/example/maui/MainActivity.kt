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
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.maui.data.ChatRepository
import com.example.maui.telemetry.LatencyLogger
import com.example.maui.telemetry.ResourceLogger
import com.example.maui.ui.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private lateinit var recyclerView: RecyclerView
  private lateinit var editTextMessage: EditText
  private lateinit var buttonSend: Button
  private lateinit var buttonPrintLog: Button
  private lateinit var promptsSpinner: android.widget.Spinner
  private lateinit var switchCannedServer: android.widget.Switch
  private lateinit var chatAdapter: ChatAdapter

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

    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    setContentView(R.layout.activity_main)

    recyclerView = findViewById(R.id.recyclerView)
    editTextMessage = findViewById(R.id.editTextMessage)
    buttonSend = findViewById(R.id.buttonSend)
    buttonPrintLog = findViewById(R.id.buttonPrintLog)
    promptsSpinner = findViewById(R.id.promptsSpinner)
    switchCannedServer = findViewById(R.id.switchCannedServer)

    val examplePrompts =
      listOf(
        "Select a frequently asked question...",
        "Show me 5 coffee shops near South Lake Union in Seattle",
        "Is the Edgewater Hotel in Seattle a good hotel?",
        "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?",
        "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)",
        "Give me a 3 day itinerary for a family of 3 traveling to London",
      )

    val adapter =
      android.widget.ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        examplePrompts,
      )
    promptsSpinner.adapter = adapter

    promptsSpinner.onItemSelectedListener =
      object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
          parent: android.widget.AdapterView<*>?,
          view: View?,
          position: Int,
          id: Long,
        ) {
          if (position > 0) {
            editTextMessage.setText(examplePrompts[position])
          }
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
      }

    buttonPrintLog.visibility = View.GONE

    chatAdapter =
      ChatAdapter(
        onGmpA2UIViewRendered = { position, latencyMs, status ->
          lifecycleScope.launch {
            latencyLogger.logLatency("A2UI", latencyMs, status)

            // Give the RecyclerView a brief moment to finish laying out the A2UIView
            // with its newly rendered, dynamic height before scrolling.
            // This prevents inaccurate scrolling offsets.
            kotlinx.coroutines.delay(100)
            val layoutManager =
              recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            val scrollPosition = if (position > 0) position - 1 else position
            layoutManager?.scrollToPositionWithOffset(scrollPosition, 0)
          }
        },
        onAgentAction = { actionName, contextJson ->
          viewModel.handleAgentAction(actionName, contextJson)
        },
      )
    recyclerView.setItemViewCacheSize(10)
    recyclerView.adapter = chatAdapter

    buttonSend.setOnClickListener {
      val messageText = editTextMessage.text.toString().trim()
      if (messageText.isNotEmpty()) {
        val radioGroundingVertex =
          findViewById<android.widget.RadioButton>(R.id.radioGroundingVertex)
        viewModel.sendMessage(
          messageText,
          radioGroundingVertex.isChecked,
          switchCannedServer.isChecked,
        )
        editTextMessage.text.clear()
      }
    }

    lifecycleScope.launch {
      viewModel.uiState.collect { messages ->
        chatAdapter.updateMessages(messages)
        scrollToLastMessage(messages.size)
      }
    }
  }

  private fun scrollToLastMessage(size: Int) {
    if (size > 0 && recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
      recyclerView.post { recyclerView.scrollToPosition(size - 1) }
    }
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}
