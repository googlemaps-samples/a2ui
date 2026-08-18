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

package com.example.maui.telemetry

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LatencyLogger(context: Context) {
  private val applicationContext = context.applicationContext
  private val latencyLogFile = "latency_log.csv"

  suspend fun logLatency(type: String, latencyMs: Long, status: String) {
    withContext(Dispatchers.IO) {
      try {
        val file = File(applicationContext.filesDir, latencyLogFile)
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

  suspend fun printLatencyLog() {
    withContext(Dispatchers.IO) {
      try {
        val file = File(applicationContext.filesDir, latencyLogFile)
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

  companion object {
    private const val TAG = "LatencyLogger"
    private const val LATENCY_TAG = "[MAUI] LatencyLog"
  }
}
