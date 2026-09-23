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
import com.google.android.libraries.mapsplatform.a2ui.A2AResponseParser
import com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
      baseUrlOverride
        ?: when (activeServer) {
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
    withContext(Dispatchers.IO) {
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
      if (mapObj.has(text)) {
        val filename = mapObj.getString(text)
        val jsonString =
          applicationContext.assets.open("canned_responses/$filename").bufferedReader().use {
            it.readText()
          }
        return JSONObject(jsonString)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading mapping.json or canned response", e)
    }
    return null
  }

  private fun buildRequest(
    partsArray: JSONArray,
    useStreaming: Boolean = DEFAULT_USE_STREAMING,
  ): Request {
    val requestBuilder = Request.Builder().addHeader("Content-Type", "application/json")

    if (activeServer == ServerType.DEMO) {
      requestBuilder.addHeader("X-A2A-Extensions", "https://a2ui.org/a2a-extension/a2ui/v0.9")
    }

    val json =
      JSONObject().apply {
        put("jsonrpc", JSON_RPC_VERSION)
        put("method", rpcMethod(useStreaming))
        put("id", 1)
        if (activeSessionId != null) {
          put("sessionId", activeSessionId)
        }
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

    val urlString = if (activeServer == ServerType.DEMO) "$baseUrl/?key=$apiKey" else baseUrl
    requestBuilder.url(urlString).post(body)

    if (activeServer == ServerType.VANILLA) {
      requestBuilder.addHeader("x-api-key", apiKey)
    }

    return requestBuilder.build()
  }

  fun callPythonServer(userMessage: JSONObject): Flow<Result<List<ParsedA2AEvent>>> =
    flow {
        val textStr = userMessage.optString("text")
        val bypassCanned = userMessage.optBoolean("bypassCanned", false)
        val useStreaming = userMessage.optBoolean("useStreaming", DEFAULT_USE_STREAMING)
        val cannedResponse = if (!bypassCanned) getCannedResponse(textStr) else null

        if (cannedResponse != null) {
          delay(2000) // Simulate network delay
          val payload = cannedResponse.optJSONObject("result") ?: cannedResponse
          val parsed = A2AResponseParser.parse(payload)
          emit(Result.success(parsed))
          return@flow
        }

        discoverProtocol() // Just to ensure session is logged if needed
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

        val request = buildRequest(partsArray, useStreaming)

        try {
          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              emit(Result.failure(Exception("Error: ${response.code} - ${response.message}")))
              return@use
            }

            suspend fun emitParsedData(rawJson: JSONObject) {
              val payload = rawJson.optJSONObject("result") ?: rawJson
              val errorObj = rawJson.opt("error") ?: payload.opt("error")
              if (errorObj != null) {
                val errorMessage =
                  (errorObj as? JSONObject)?.optString("message")?.takeIf { it.isNotEmpty() }
                    ?: errorObj.toString()
                emit(Result.failure(Exception("Server Error: $errorMessage")))
                return
              }

              val kind = payload.optString("kind")
              if (useStreaming && kind == "task") return

              var finalPayloadToParse = payload
              if (kind == "status-update" || kind == "task") {
                val statusObj = payload.optJSONObject("status")
                val messageObj = statusObj?.optJSONObject("message")
                if (messageObj != null) {
                  finalPayloadToParse = messageObj
                }
              }

              val parsedEvents = A2AResponseParser.parse(finalPayloadToParse)
              emit(Result.success(parsedEvents))
            }

            if (!useStreaming) {
              val responseBody = response.body?.string() ?: return@use
              emitParsedData(JSONObject(responseBody))
              return@use
            }

            val source = response.body?.source() ?: return@use

            val buffer = StringBuilder()

            while (!source.exhausted()) {
              if (!currentCoroutineContext().isActive) {
                break
              }
              val line = source.readUtf8Line()

              if (line != null) {
                var dataString = ""
                val isSseMetadataOrComment =
                  line.startsWith("id:") ||
                    line.startsWith("event:") ||
                    line.startsWith(":") ||
                    line.startsWith("retry:")

                if (line.startsWith(SSE_DATA_PREFIX)) {
                  dataString = line.removePrefix(SSE_DATA_PREFIX)
                  if (dataString == SSE_DONE_MESSAGE) {
                    break
                  }
                } else if (line.isNotEmpty() && !isSseMetadataOrComment) {
                  dataString = line
                }

                if (dataString.isNotEmpty()) {
                  buffer.append(dataString).append("\n")
                  val currentBufferString = buffer.toString().trim()

                  if (currentBufferString.startsWith("{")) {
                    try {
                      val rawJson = JSONObject(currentBufferString)
                      buffer.clear()
                      emitParsedData(rawJson)
                    } catch (e: CancellationException) {
                      throw e
                    } catch (e: Exception) {
                      // If JSONObject threw JSONException, keep the incomplete fragment in buffer
                      // to reassemble with the next line. If JSONObject succeeded, buffer is
                      // already cleared above.
                    }
                  }
                }
              }
            }
          }
        } catch (e: IOException) {
          emit(Result.failure(Exception("Network Error: ${e.message}")))
        }
      }
      .flowOn(Dispatchers.IO)

  companion object {
    const val DEFAULT_USE_STREAMING = true

    fun rpcMethod(useStreaming: Boolean): String =
      if (useStreaming) "message/stream" else "message/send"

    private const val TAG = "ChatRepository"
    private const val SSE_DATA_PREFIX = "data: "
    private const val SSE_DONE_MESSAGE = "[DONE]"
    private const val JSON_RPC_VERSION = "2.0"

    /**
     * Overrides the backend base URL, e.g. "https://maui-backend.google.com".
     *
     * Set by integration tests that drive the app against a server other than the one baked in at
     * build time. `null`, the default, keeps [BuildConfig.GATEWAY_URL].
     */
    @Volatile @JvmStatic var baseUrlOverride: String? = null
  }
}
