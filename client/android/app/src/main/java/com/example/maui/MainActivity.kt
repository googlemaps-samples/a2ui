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

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicInteger

val traceCookie = AtomicInteger(0)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonPrintLog: Button
    private lateinit var promptsSpinner: android.widget.Spinner
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    enum class ServerType {
        DEMO, VANILLA
    }

    enum class DeviceType {
        PHYSICAL, EMULATOR
    }

    // --- CONFIGURATION ---
    private val activeServer = ServerType.DEMO
    private val deviceType = DeviceType.EMULATOR
    // ---------------------

    private val baseUrl: String
        get() = when (activeServer) {
            ServerType.DEMO -> BuildConfig.GATEWAY_URL
            ServerType.VANILLA -> when (deviceType) {
                DeviceType.PHYSICAL -> "http://127.0.0.1:10002"
                DeviceType.EMULATOR -> "http://10.0.2.2:10002"
            }
        }

    private val appName: String
        get() = when (activeServer) {
            ServerType.DEMO -> "hello_world_agent"
            ServerType.VANILLA -> "my_agent"
        }

    private var activeSessionId: String? = null
    private var useSseProtocol = false
    private var hasDiscoveredProtocol = false
    
    private var currentActiveCall: okhttp3.Call? = null

    private var currentAgentTextIndex: Int? = null
    private var currentAgentA2UIIndex: Int? = null

    private val contextId = java.util.UUID.randomUUID().toString()
    private val latencyLogFile = "latency_log.csv"
    private val resourceLogFile = "resource_log.csv"

    private val SHOW_PRINT_LOG_BUTTON = false

    private var lastCpuTime: Long = 0
    private val cpuJiffyToMs = 10L
    private val loggingIntervalMs = 1000L

    private val resourceLoggingScope = CoroutineScope(Dispatchers.IO)
    private var resourceLoggingJob: Job? = null
    private val numberOfCores = Runtime.getRuntime().availableProcessors()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.google.android.libraries.mapsplatform.a2ui.A2UIServices.provideAPIKey(BuildConfig.MAPS_API_KEY)
        
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonPrintLog = findViewById(R.id.buttonPrintLog)
        promptsSpinner = findViewById(R.id.promptsSpinner)

        val examplePrompts = listOf(
            "Select a frequently asked question...",
            "Show me 5 coffee shops near South Lake Union in Seattle",
            "Is the Edgewater Hotel in Seattle a good hotel?",
            "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?",
            "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)",
            "Give me a 3 day itinerary for a family of 3 traveling to London"
        )

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, examplePrompts)
        promptsSpinner.adapter = adapter

        promptsSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    editTextMessage.setText(examplePrompts[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (SHOW_PRINT_LOG_BUTTON) {
            buttonPrintLog.visibility = View.VISIBLE
        } else {
            buttonPrintLog.visibility = View.GONE
        }

        chatAdapter = ChatAdapter(messages) { position, latencyMs, status ->
            logLatency("A2UI", latencyMs, status)
            
            // Auto-scroll to show the whole conversation (Question + Answer)
            // We scroll to position - 1 to ensure the user's question stays visible at the top
            lifecycleScope.launch {
                delay(100)
                if (position == messages.size - 1 || position == messages.size - 2) {
                    val scrollPosition = if (position > 0) position - 1 else position
                    val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(scrollPosition, 0)
                }
            }
        }
        recyclerView.setItemViewCacheSize(MAX_ITEM_VIEW_CACHE_SIZE)
        recyclerView.adapter = chatAdapter

        buttonSend.setOnClickListener {
            val messageText = editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                currentActiveCall?.cancel()
                currentActiveCall = null
                currentAgentTextIndex = null
                currentAgentA2UIIndex = null
                var serverMessageText = messageText
                val radioGroundingVertex = findViewById<android.widget.RadioButton>(R.id.radioGroundingVertex)
                if (radioGroundingVertex.isChecked) {
                    serverMessageText = "[GROUNDING] $messageText"
                }

                addMessage(ChatMessage.Text(messageText, true))
                editTextMessage.text.clear()
                val jsonObject = JSONObject()
                jsonObject.put("text", serverMessageText)
                callPythonServer(jsonObject)
            }
        }

        buttonPrintLog.setOnClickListener {
            printResourceLogToLogcat()
            printLatencyLogToLogcat()
        }

        startResourceLogging()
    }

    private fun startResourceLogging() {
        resourceLoggingJob?.cancel()
        resourceLoggingJob = resourceLoggingScope.launch {
            while (isActive) {
                logResourceUsage()
                delay(loggingIntervalMs)
            }
        }
    }

    private fun logResourceUsage() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pid = Process.myPid()
            val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))
            val debugMemoryInfo = memoryInfo[0]
            val totalPss = debugMemoryInfo.totalPss
            val dalvikPss = debugMemoryInfo.dalvikPss
            val nativePss = debugMemoryInfo.nativePss
            val otherPss = debugMemoryInfo.otherPss

            var cpuTimeDeltaMs = 0L
            var cpuPercentage = 0.0
            try {
                val statFile = File("/proc/$pid/stat")
                BufferedReader(FileReader(statFile)).use { reader ->
                    val line = reader.readLine()
                    if (line != null) {
                        val parts = line.split(" ")
                        if (parts.size >= 17) {
                            val utime = parts[13].toLong()
                            val stime = parts[14].toLong()
                            val currentCpuTime = utime + stime

                            if (lastCpuTime > 0) {
                                val cpuJiffiesDelta = currentCpuTime - lastCpuTime
                                cpuTimeDeltaMs = cpuJiffiesDelta * cpuJiffyToMs

                                val totalAvailableCpuTimeMs = loggingIntervalMs * numberOfCores
                                if (totalAvailableCpuTimeMs > 0) {
                                    cpuPercentage = (cpuTimeDeltaMs.toDouble() / totalAvailableCpuTimeMs.toDouble()) * 100.0
                                }
                            }
                            lastCpuTime = currentCpuTime
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading /proc/$pid/stat: ${e.message}")
            }

            val file = File(filesDir, resourceLogFile)
            FileWriter(file, true).use { writer ->
                if (!file.exists() || file.length() == 0L) {
                    writer.append("Timestamp,CPU_Time_Delta_ms,CPU_Percentage,Total_PSS_KB,Dalvik_PSS_KB,Native_PSS_KB,Other_PSS_KB\n")
                }
                writer.append("$timestamp,$cpuTimeDeltaMs,${String.format("%.2f", cpuPercentage)},$totalPss,$dalvikPss,$nativePss,$otherPss\n")
            }
            Log.d(RESOURCE_LOG_TAG, "Logged: CPU Delta=${cpuTimeDeltaMs}ms, CPU%=${String.format("%.2f", cpuPercentage)}, PSS=${totalPss}KB, Dalvik=${dalvikPss}KB, Native=${nativePss}KB, Other=${otherPss}KB")

        } catch (e: Exception) {
            Log.e(TAG, "Error logging resource usage: ${e.message}")
        }
    }

    private fun printResourceLogToLogcat() {
        resourceLoggingScope.launch {
            try {
                val file = File(filesDir, resourceLogFile)
                if (file.exists()) {
                    BufferedReader(FileReader(file)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(RESOURCE_LOG_OUTPUT_TAG, line ?: "")
                        }
                    }
                    Log.d(RESOURCE_LOG_OUTPUT_TAG, "--- End of Resource Log ---")
                } else {
                    Log.d(RESOURCE_LOG_OUTPUT_TAG, "Resource log file not found.")
                }
            } catch (e: IOException) {
                Log.e(RESOURCE_LOG_OUTPUT_TAG, "Error reading resource log: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        resourceLoggingJob?.cancel()
        super.onDestroy()
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.post {
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun removeLastLoadingMessage() {
        val lastIndex = messages.size - 1
        if (lastIndex >= 0 && messages[lastIndex] is ChatMessage.Loading) {
            messages.removeAt(lastIndex)
            chatAdapter.notifyItemRemoved(lastIndex)
        }
    }

    private fun processJsonResponse(json: JSONObject) {
        if (json.has("error")) {
            val error = json.opt("error")
            val errorMsg = if (error is JSONObject) error.optString("message") else error?.toString() ?: "Unknown error"
            addMessage(ChatMessage.Text("Server Error: $errorMsg", false))
            return
        }

        val parsedParts = try {
            com.google.android.libraries.mapsplatform.a2ui.A2AResponseParser.parse(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse A2UI payload", e)
            emptyList<com.google.android.libraries.mapsplatform.a2ui.ParsedA2AEvent>()
        }

        // Because the streaming data might be parsed into several incomplete ParsedA2AEvent.Data blocks,
        // we extract and aggregate them into a single, valid JSON array here to prevent crashes when passed to the frontend.
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Error aggregating JSON data", e)
                        }
                    }
                }
            }
        }

        val finalConversationalText = aggregatedText.toString()
        val finalA2uiJson = if (aggregatedJson.length() > 0) aggregatedJson.toString() else ""

        if (finalConversationalText.isNotEmpty() || finalA2uiJson.isNotEmpty()) {
            if (finalConversationalText.isNotEmpty()) {
                currentAgentTextIndex?.let { idx ->
                    messages[idx] = ChatMessage.Text(finalConversationalText, false)
                    chatAdapter.notifyItemChanged(idx)
                } ?: run {
                    messages.add(ChatMessage.Text(finalConversationalText, false))
                    val newIdx = messages.size - 1
                    currentAgentTextIndex = newIdx
                    chatAdapter.notifyItemInserted(newIdx)
                    scrollToLastMessage()
                }
            }
            if (finalA2uiJson.isNotEmpty() && finalA2uiJson != "[]") {
                currentAgentA2UIIndex?.let { idx ->
                    val oldMsg = messages[idx] as? ChatMessage.GmpA2UIView
                    messages[idx] = ChatMessage.GmpA2UIView(finalA2uiJson, oldMsg?.startTime ?: System.currentTimeMillis())
                    
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(idx)
                    if (viewHolder is ChatAdapter.GmpA2UIViewHolder) {
                        viewHolder.updateA2uiJson(finalA2uiJson)
                    } else {
                        chatAdapter.notifyItemChanged(idx)
                    }
                } ?: run {
                    val gmpViewStartTime = System.currentTimeMillis()
                    messages.add(ChatMessage.GmpA2UIView(finalA2uiJson, gmpViewStartTime))
                    val newIdx = messages.size - 1
                    currentAgentA2UIIndex = newIdx
                    chatAdapter.notifyItemInserted(newIdx)
                    scrollToLastMessage()
                }
            }
        }
    }


    private fun logLatency(type: String, latencyMs: Long, status: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filesDir, latencyLogFile)
                val writer = FileWriter(file, true) // Append mode

                if (!file.exists() || file.length() == 0L) {
                    writer.append("Timestamp,Type,Latency (ms),Status\n")
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                writer.append("$timestamp,$type,$latencyMs,$status\n")
                writer.flush()
                writer.close()
                Log.d(TAG, "$type Latency logged: $latencyMs ms, Status: $status")
            } catch (e: IOException) {
                Log.e(TAG, "Error logging latency: ${e.message}")
            }
        }
    }

    private fun printLatencyLogToLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filesDir, latencyLogFile)
                if (file.exists()) {
                    BufferedReader(FileReader(file)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(LATENCY_TAG, line ?: "")
                        }
                    }
                    Log.d(LATENCY_TAG, "--- End of Latency Log ---")
                } else {
                    Log.d(LATENCY_TAG, "Latency log file not found.")
                }
            } catch (e: IOException) {
                Log.e(LATENCY_TAG, "Error reading latency log: ${e.message}")
            }
        }
    }

    private val apiKey = BuildConfig.GATEWAY_API_KEY

    private fun discoverProtocol() {
        if (hasDiscoveredProtocol) {
            return
        }
        hasDiscoveredProtocol = true

        val request = Request.Builder()
            .url("$baseUrl/apps/$appName/users/user/sessions?key=$apiKey")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string() ?: ""
                    if (body.isNotEmpty()) {
                        try {
                            val json = JSONObject(body)
                            val id = if (json.has("id")) json.optString("id") else null
                            if (id != null && id.isNotEmpty()) {
                                activeSessionId = id
                                useSseProtocol = true
                                Log.d(TAG, "Discovered ADK Web Server (SSE) protocol")
                            } else {
                                useSseProtocol = false
                                Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol")
                            }
                        } catch(e: Exception) {
                            useSseProtocol = false
                            Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol")
                        }
                    } else {
                        useSseProtocol = false
                        Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol")
                    }
                } else {
                    useSseProtocol = false
                    Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol")
                }
            }
        } catch (e: IOException) {
            useSseProtocol = false
            Log.d(TAG, "Discovered Standalone (JSON-RPC) protocol on failure")
        }
    }

    public fun callPythonServer(userMessage: JSONObject) {
        runOnUiThread { addMessage(ChatMessage.Loading) }
        
        // --- CANNED PROMPT INTERCEPTION ---
        val textStr = userMessage.optString("text")
        
        var fileMap: Map<String, String> = emptyMap()
        try {
            val mappingJson = assets.open("canned_responses/mapping.json").bufferedReader().use { it.readText() }
            val mapObj = JSONObject(mappingJson)
            val tempMap = mutableMapOf<String, String>()
            for (key in mapObj.keys()) {
                val value = mapObj.getString(key)
                // Ensure value points to the correct new directory name
                val correctValue = if (value.startsWith("prompt_")) "canned_responses/$value" else value.replace("canned_prompts", "canned_responses")
                tempMap[key] = correctValue
            }
            fileMap = tempMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading mapping.json", e)
        }
        
        if (textStr.isNotEmpty()) {
            val fileName = fileMap[textStr]
            if (fileName != null) {
                try {
                    val jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
                    val cannedResponse = JSONObject(jsonString)
                    Log.d(TAG, "Using canned response from $fileName")
                    // Simulate network delay
                    lifecycleScope.launch(Dispatchers.IO) {
                        delay(2000)
                        runOnUiThread { 
                            removeLastLoadingMessage()
                            processJsonResponse(cannedResponse) 
                        }
                    }
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading canned response", e)
                }
            }
        }

        val startTime = System.currentTimeMillis()
        val currentCookie = traceCookie.incrementAndGet()
        // Trace.beginAsyncSection("Server Response", currentCookie)
        lifecycleScope.launch(Dispatchers.IO) {
            discoverProtocol()
            val partsArray = JSONArray()
            if (userMessage.has("text")) {
                partsArray.put(JSONObject().apply {
                    put("text", userMessage.optString("text"))
                })
            } else if (userMessage.has("userAction")) {
                partsArray.put(JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("userAction", userMessage.opt("userAction"))
                    })
                })
            }

            val request: Request
            if (useSseProtocol) {
                val json = JSONObject().apply {
                    put("appName", appName)
                    put("userId", "user")
                    put("sessionId", activeSessionId ?: "")
                    put("newMessage", JSONObject().apply {
                        put("role", "user")
                        put("parts", partsArray)
                    })
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/run_sse")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                if (activeServer == ServerType.DEMO) {
                    requestBuilder.addHeader("X-A2A-Extensions", "https://a2ui.org/a2a-extension/a2ui/v0.9")
                }
                request = requestBuilder.build()
            } else {
                val json = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "message/send")
                    put("id", 1)
                    put("params", JSONObject().apply {
                        put("message", JSONObject().apply {
                            put("role", "user")
                            put("messageId", UUID.randomUUID().toString())
                            put("contextId", contextId)
                            put("parts", partsArray)
                        })
                    })
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/?key=$apiKey")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                if (activeServer == ServerType.DEMO) {
                    requestBuilder.addHeader("X-A2A-Extensions", "https://a2ui.org/a2a-extension/a2ui/v0.9")
                }
                request = requestBuilder.build()
            }

            try {
                currentActiveCall = client.newCall(request)
                currentActiveCall?.execute()?.use { response ->
                    val endTime = System.currentTimeMillis()
                    val latency = endTime - startTime
                    // Trace.endAsyncSection("Server Response", currentCookie)
                    logLatency("Server", latency, if (response.isSuccessful) "Success" else "Failure")
                    runOnUiThread { removeLastLoadingMessage() }
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            addMessage(ChatMessage.Text("Error: ${response.code} - ${response.message}", false))
                        }
                        return@use
                    }
                    handleSuccessfulResponse(response)
                }
            } catch (e: IOException) {
                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime
                // Trace.endAsyncSection("Server Response", currentCookie)
                logLatency("Server", latency, "Network Error")
                runOnUiThread {
                    removeLastLoadingMessage()
                    addMessage(ChatMessage.Text("Network Error: ${e.message}", false))
                }
            }
        }
    }

    // The LLM's streaming response (SSE) arrives in fragmented chunks.
    // We use this StringBuilder to accumulate all the text chunks and assemble them into a complete JSON string before parsing.
    private var globalSseAccumulator = StringBuilder()

    private fun handleSuccessfulResponse(response: okhttp3.Response) {
        if (useSseProtocol || response.header("Content-Type")?.contains("text/event-stream") == true) {
            val source = response.body.source() ?: return
            globalSseAccumulator.clear()
            while (!source.exhausted()) {
                val line = source.readUtf8Line()
                if (line != null && line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data.isNotEmpty() && data != "[DONE]") {
                        try {
                            val jsonObj = JSONObject(data)
                            // Extract text delta to accumulate
                            var textDelta = ""
                            if (jsonObj.has("parts")) {
                                val parts = jsonObj.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val p = parts.optJSONObject(i)
                                        if (p != null && p.has("text")) {
                                            textDelta += p.optString("text")
                                        }
                                    }
                                }
                            }
                            if (textDelta.isNotEmpty()) {
                                globalSseAccumulator.append(textDelta)
                            }
                            
                            // 1. Update the native Android text bubble with the accumulated conversation text
                            if (globalSseAccumulator.isNotEmpty()) {
                                val textUpdate = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", globalSseAccumulator.toString())))
                                runOnUiThread { processJsonResponse(textUpdate) }
                            }

                            // 2. Pass the raw JSON chunk to the WebView. 
                            // The frontend (AppMobile.tsx) now handles hallucination fixes, path resolution, and text deduplication.
                            runOnUiThread { processJsonResponse(jsonObj) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing SSE data: $data", e)
                        }
                    }
                }
            }
        } else {
            val responseData = response.body.string() ?: return
            runOnUiThread {
                try {
                    val jsonResponse = JSONObject(responseData)
                    val result = jsonResponse.opt("result")
                    if (result is JSONObject) {
                        processJsonResponse(result)
                    } else if (result is String) {
                        try {
                            val inner = JSONObject(result)
                            processJsonResponse(inner)
                        } catch (e: Exception) {
                            processJsonResponse(jsonResponse)
                        }
                    } else {
                        processJsonResponse(jsonResponse)
                    }
                } catch (e: Exception) {
                    addMessage(ChatMessage.Text("Error parsing JSON: ${e.message}", false))
                }
            }
        }
    }

    public fun scrollToLastMessage() {
        runOnUiThread {
            if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                recyclerView.post {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val LATENCY_TAG = "LatencyLog"
        private const val RESOURCE_LOG_TAG = "ResourceLog"
        private const val RESOURCE_LOG_OUTPUT_TAG = "ResourceLogOutput"
        private const val MAX_ITEM_VIEW_CACHE_SIZE = 10
    }
}
