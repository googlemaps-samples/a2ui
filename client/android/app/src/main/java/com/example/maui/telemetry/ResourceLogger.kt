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

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResourceLogger(context: Context) {
  private val applicationContext = context.applicationContext
  private val resourceLogFile = "resource_log.csv"
  private var lastCpuTime: Long = 0
  private val cpuJiffyToMs = 10L
  private val loggingIntervalMs = 1000L
  private var resourceLoggingJob: Job? = null
  private val numberOfCores = Runtime.getRuntime().availableProcessors()

  fun startLogging(scope: CoroutineScope) {
    resourceLoggingJob?.cancel()
    resourceLoggingJob =
      scope.launch(Dispatchers.IO) {
        while (isActive) {
          logResourceUsage()
          delay(loggingIntervalMs)
        }
      }
  }

  fun stopLogging() {
    resourceLoggingJob?.cancel()
  }

  private fun logResourceUsage() {
    try {
      val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
      val activityManager =
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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

              if (lastCpuTime > 0L) {
                val cpuJiffiesDelta = currentCpuTime - lastCpuTime
                cpuTimeDeltaMs = cpuJiffiesDelta * cpuJiffyToMs

                val totalAvailableCpuTimeMs = loggingIntervalMs * numberOfCores
                if (totalAvailableCpuTimeMs > 0) {
                  cpuPercentage =
                    (cpuTimeDeltaMs.toDouble() / totalAvailableCpuTimeMs.toDouble()) * 100.0
                }
              }
              lastCpuTime = currentCpuTime
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error reading /proc/$pid/stat: ${e.message}")
      }

      val file = File(applicationContext.filesDir, resourceLogFile)
      FileWriter(file, true).use { writer ->
        if (!file.exists() || file.length() == 0L) {
          writer.append(
            "Timestamp,CPU_Time_Delta_ms,CPU_Percentage,Total_PSS_KB,Dalvik_PSS_KB,Native_PSS_KB,Other_PSS_KB\n"
          )
        }
        writer.append(
          "$timestamp,$cpuTimeDeltaMs,${String.format("%.2f", cpuPercentage)},$totalPss,$dalvikPss,$nativePss,$otherPss\n"
        )
      }
      Log.d(
        RESOURCE_LOG_TAG,
        "Logged: CPU Delta=${cpuTimeDeltaMs}ms, CPU%=${String.format("%.2f", cpuPercentage)}, PSS=${totalPss}KB, Dalvik=${dalvikPss}KB, Native=${nativePss}KB, Other=${otherPss}KB",
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error logging resource usage: ${e.message}")
    }
  }

  suspend fun printResourceLog() {
    withContext(Dispatchers.IO) {
      try {
        val file = File(applicationContext.filesDir, resourceLogFile)
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

  companion object {
    private const val TAG = "ResourceLogger"
    private const val RESOURCE_LOG_TAG = "ResourceLog"
    private const val RESOURCE_LOG_OUTPUT_TAG = "ResourceLogOutput"
  }
}
