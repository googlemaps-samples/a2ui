//
// Copyright 2026 Google Inc.
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

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onGmpA2UIViewRendered: (position: Int, latencyMs: Long, status: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_TEXT = 1
    private val VIEW_TYPE_GMPA2UIVIEW = 2
    private val VIEW_TYPE_LOADING = 3

    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
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
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_maui_gmp_a2ui_view, parent, false)
                GmpA2UIViewHolder(view, parent.context as? MainActivity, onGmpA2UIViewRendered)
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val message = messages[position]) {
            is ChatMessage.Text -> (holder as TextViewHolder).bind(message)
            is ChatMessage.GmpA2UIView -> {
                (holder as GmpA2UIViewHolder).bind(message.a2uiJsonString, message.startTime, position == messages.size - 1)
            }
            is ChatMessage.Loading -> { /* No binding needed for loading state */ }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is GmpA2UIViewHolder) {
            // Clearing logic if needed in the future
        }
    }

    override fun getItemCount(): Int = messages.size

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

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ProgressBar is self-animating, no binding needed
    }

    class GmpA2UIViewHolder(
        itemView: android.view.View,
        mainActivity: MainActivity?,
        private val onGmpA2UIViewRendered: (position: Int, latencyMs: Long, status: String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        val gmpA2UIView: com.google.android.libraries.mapsplatform.a2ui.A2UIView = itemView.findViewById(R.id.gmpA2UIView)

        init {
            gmpA2UIView.onRenderComplete = { latency, status ->

                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onGmpA2UIViewRendered(adapterPosition, latency, status)
                }
            }
            gmpA2UIView.onUserAction = { actionJson ->
                if (mainActivity != null) {
                    try {
                        val context = org.json.JSONObject(actionJson)
                        val userAction = org.json.JSONObject().apply {
                            put("name", "get_directions")
                            put("context", context)
                        }
                        val jsonObject = org.json.JSONObject().apply {
                            put("userAction", userAction)
                        }
                        mainActivity.callPythonServer(jsonObject)
                    } catch (e: Exception) {}
                }
            }
        }

        fun bind(serverResponse: String, startTime: Long?, isLatestResponse: Boolean) {
            gmpA2UIView.render(serverResponse, startTime)
        }

        fun updateA2uiJson(newJson: String) {
            gmpA2UIView.updateA2uiJson(newJson)
        }
    }

    companion object {
        private const val TAG = "ChatAdapter"
    }
}