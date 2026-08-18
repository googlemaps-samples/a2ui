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

package com.example.maui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.mapsplatform.a2ui.A2UIView

class ChatAdapter(
  private val onGmpA2UIViewRendered: (position: Int, latencyMs: Long, status: String) -> Unit,
  private val onAgentAction: (actionName: String, contextJson: String) -> Unit,
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

  fun updateMessages(newMessages: List<ChatMessage>) {
    submitList(newMessages)
  }

  private val VIEW_TYPE_TEXT = 1
  private val VIEW_TYPE_GMPA2UIVIEW = 2
  private val VIEW_TYPE_LOADING = 3

  override fun getItemViewType(position: Int): Int {
    return when (getItem(position)) {
      is ChatMessage.Text -> VIEW_TYPE_TEXT
      is ChatMessage.GmpA2UIView -> VIEW_TYPE_GMPA2UIVIEW
      is ChatMessage.Loading -> VIEW_TYPE_LOADING
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    return when (viewType) {
      VIEW_TYPE_TEXT -> {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_text, parent, false)
        TextViewHolder(view)
      }
      VIEW_TYPE_GMPA2UIVIEW -> {
        val view =
          LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maui_gmp_a2ui_view, parent, false)
        GmpA2UIViewHolder(view, onGmpA2UIViewRendered, onAgentAction)
      }
      VIEW_TYPE_LOADING -> {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false)
        LoadingViewHolder(view)
      }
      else -> throw IllegalArgumentException("Invalid view type")
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val message = getItem(position)) {
      is ChatMessage.Text -> (holder as TextViewHolder).bind(message)
      is ChatMessage.GmpA2UIView -> {
        (holder as GmpA2UIViewHolder).bind(
          message.a2uiJsonString,
          message.startTime,
          position == itemCount - 1,
        )
      }
      is ChatMessage.Loading -> {}
    }
  }

  class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textView: TextView = itemView.findViewById(R.id.textViewMessage)

    fun bind(message: ChatMessage.Text) {
      textView.text = message.text
      val layoutParams = textView.layoutParams as ViewGroup.MarginLayoutParams
      if (message.isUser) {
        textView.setBackgroundResource(R.drawable.rounded_corner_user)
      } else {
        textView.setBackgroundResource(R.drawable.rounded_corner)
      }
      textView.layoutParams = layoutParams
    }
  }

  class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {}

  class GmpA2UIViewHolder(
    itemView: View,
    private val onGmpA2UIViewRendered: (position: Int, latencyMs: Long, status: String) -> Unit,
    private val onAgentAction: (actionName: String, contextJson: String) -> Unit,
  ) : RecyclerView.ViewHolder(itemView) {
    val a2uiView: A2UIView = itemView.findViewById(R.id.gmpA2UIView)

    init {
      a2uiView.onRenderComplete = { latencyMs, status ->
        if (adapterPosition != RecyclerView.NO_POSITION) {
          onGmpA2UIViewRendered(adapterPosition, latencyMs, status)
        }
      }
      a2uiView.onUserAction = { actionJson -> onAgentAction("get_directions", actionJson) }
    }

    fun bind(serverResponse: String, startTime: Long?, isLatestResponse: Boolean) {
      a2uiView.render(serverResponse, startTime)
    }
  }

  companion object {
    private const val TAG = "ChatAdapter"
  }
}

class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
  override fun areItemsTheSame(oldChatMessage: ChatMessage, newChatMessage: ChatMessage): Boolean {
    return oldChatMessage == newChatMessage
  }

  override fun areContentsTheSame(
    oldChatMessage: ChatMessage,
    newChatMessage: ChatMessage,
  ): Boolean {
    return oldChatMessage == newChatMessage
  }
}
