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

package com.example.maui.data

import android.content.Context
import android.util.Log
import com.example.maui.BuildConfig
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(private val context: Context) {
  private val applicationContext = context.applicationContext
  private var useSseProtocol: Boolean? = null
  private var activeSessionId: String? = null
  private var hasDiscoveredProtocol = false
  private val discoveryMutex = Mutex()

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(300, TimeUnit.SECONDS)
      .readTimeout(300, TimeUnit.SECONDS)
      .writeTimeout(300, TimeUnit.SECONDS)
      .build()

  enum class ServerType {
    DEMO,
    VANILLA,
  }

  enum class DeviceType {
    PHYSICAL,
    EMULATOR,
  }

  // --- CONFIGURATION ---
  val activeServer = ServerType.DEMO
  private val deviceType = DeviceType.EMULATOR
  // ---------------------

  val baseUrl: String
    get() =
      when (activeServer) {
        ServerType.DEMO -> BuildConfig.GATEWAY_URL
        ServerType.VANILLA ->
          when (deviceType) {
            DeviceType.PHYSICAL -> "http://127.0.0.1:10002"
            DeviceType.EMULATOR -> "http://10.0.2.2:10002"
          }
      }

  val appName: String
    get() =
      when (activeServer) {
        ServerType.DEMO -> "hello_world_agent"
        ServerType.VANILLA -> "my_agent"
      }

  private val apiKey = BuildConfig.GATEWAY_API_KEY
  private val contextId = UUID.randomUUID().toString()

  suspend fun discoverProtocol(): Boolean =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
      discoveryMutex.withLock {
        if (hasDiscoveredProtocol) {
          return@withContext useSseProtocol ?: false
        }
        hasDiscoveredProtocol = true

        val request =
          Request.Builder()
            .url("$baseUrl/apps/$appName/users/user/sessions?key=$apiKey")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        try {
          client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              val body = response.body?.string() ?: ""
              if (body.isNotEmpty()) {
                try {
                  val json = JSONObject(body)
                  val id = if (json.has("id")) json.optString("id") else null
                  if (id != null && id.isNotEmpty()) {
                    activeSessionId = id
                    useSseProtocol = true
                    Log.d(TAG, "Discovered ADK Web Server (SSE) protocol")
                    return@withContext true
                  }
                } catch (e: Exception) {
                  Log.d(TAG, "Error parsing session response", e)
                }
              }
            }
          }
        } catch (e: IOException) {
          Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol on failure")
        }
        useSseProtocol = false
        Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol")
        return@withContext false
      }
    }

  fun isCannedPrompt(text: String): Boolean {
    return getCannedResponse(text) != null
  }

  fun getCannedResponse(text: String): JSONObject? {
    try {
      val mappingJson =
        applicationContext.assets.open("canned_responses/mapping.json").bufferedReader().use {
          it.readText()
        }
      val mapObj = JSONObject(mappingJson)
      for (key in mapObj.keys()) {
        if (key == text) {
          val value = mapObj.getString(key)
          val correctValue =
            if (value.startsWith("prompt_")) "canned_responses/$value"
            else value.replace("canned_prompts", "canned_responses")
          val jsonString =
            applicationContext.assets.open(correctValue).bufferedReader().use { it.readText() }
          return JSONObject(jsonString)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading mapping.json or canned response", e)
    }
    return null
  }

  private fun buildRequest(useSse: Boolean, partsArray: JSONArray): Request {
    val requestBuilder = Request.Builder().addHeader("Content-Type", "application/json")

    if (activeServer == ServerType.DEMO) {
      requestBuilder.addHeader("X-A2A-Extensions", "https://a2ui.org/a2a-extension/a2ui/v0.9")
    }

    if (useSse) {
      val json =
        JSONObject().apply {
          put("appName", appName)
          put("userId", "user")
          put("sessionId", activeSessionId ?: "")
          put(
            "newMessage",
            JSONObject().apply {
              put("role", "user")
              put("parts", partsArray)
            },
          )
        }
      val body = json.toString().toRequestBody("application/json".toMediaType())
      requestBuilder.url("$baseUrl/run_sse").post(body)
    } else {
      val json =
        JSONObject().apply {
          put("jsonrpc", JSON_RPC_VERSION)
          put("method", "message/send")
          put("id", 1)
          put(
            "params",
            JSONObject().apply {
              put(
                "message",
                JSONObject().apply {
                  put("role", "user")
                  put("messageId", UUID.randomUUID().toString())
                  put("contextId", contextId)
                  put("parts", partsArray)
                },
              )
            },
          )
        }
      val body = json.toString().toRequestBody("application/json".toMediaType())
      requestBuilder.url("$baseUrl/?key=$apiKey").post(body)
    }

    return requestBuilder.build()
  }

  private fun parseSseEvent(data: String): String {
    var textDelta = ""
    try {
      val jsonObj = JSONObject(data)
      val parts = jsonObj.optJSONArray("parts")
      if (parts != null) {
        for (i in 0 until parts.length()) {
          val p = parts.optJSONObject(i)
          if (p != null && p.has("text")) {
            textDelta += p.optString("text")
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing SSE data: $data", e)
    }
    return textDelta
  }

  fun callPythonServer(userMessage: JSONObject): Flow<Result<AgentResponse>> =
    flow {
        val textStr = userMessage.optString("text")
        val bypassCanned = userMessage.optBoolean("bypassCanned", false)
        val cannedResponse = if (!bypassCanned) getCannedResponse(textStr) else null

        if (cannedResponse != null) {
          delay(2000) // Simulate network delay
          emit(Result.success(AgentResponse("", cannedResponse.toString(), isCanned = true)))
          return@flow
        }

        val useSse = discoverProtocol()
        val partsArray = JSONArray()
        if (userMessage.has("text")) {
          partsArray.put(JSONObject().apply { put("text", userMessage.optString("text")) })
        } else if (userMessage.has("userAction")) {
          partsArray.put(
            JSONObject().apply {
              put("data", JSONObject().apply { put("userAction", userMessage.opt("userAction")) })
            }
          )
        }

        val request = buildRequest(useSse, partsArray)

        try {
          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              emit(Result.failure(Exception("Error: ${response.code} - ${response.message}")))
              return@use
            }

            if (useSse || response.header("Content-Type")?.contains("text/event-stream") == true) {
              val source = response.body?.source() ?: return@use
              val globalSseAccumulator = StringBuilder()
              while (!source.exhausted()) {
                val line = source.readUtf8Line()
                if (line != null && line.startsWith(SSE_DATA_PREFIX)) {
                  val data = line.removePrefix(SSE_DATA_PREFIX)
                  if (data.isNotEmpty() && data != SSE_DONE_MESSAGE) {
                    val textDelta = parseSseEvent(data)
                    if (textDelta.isNotEmpty()) {
                      globalSseAccumulator.append(textDelta)
                    }
                    emit(Result.success(AgentResponse(globalSseAccumulator.toString(), data)))
                  }
                }
              }
            } else {
              val responseData = response.body?.string() ?: return@use
              try {
                val jsonResponse = JSONObject(responseData)
                val resultObj = jsonResponse.opt("result")
                val finalJson =
                  if (resultObj is JSONObject) resultObj.toString()
                  else if (resultObj is String) resultObj else jsonResponse.toString()
                emit(Result.success(AgentResponse("", finalJson)))
              } catch (e: Exception) {
                emit(Result.failure(Exception("Error parsing JSON: ${e.message}")))
              }
            }
          }
        } catch (e: IOException) {
          emit(Result.failure(Exception("Network Error: ${e.message}")))
        }
      }
      .flowOn(Dispatchers.IO)

  companion object {
    private const val TAG = "ChatRepository"
    private const val SSE_DATA_PREFIX = "data: "
    private const val SSE_DONE_MESSAGE = "[DONE]"
    private const val JSON_RPC_VERSION = "2.0"
  }
}
