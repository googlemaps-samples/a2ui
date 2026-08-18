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

import Foundation

actor ResourceLogger {
  static let shared = ResourceLogger()
  private var loggingTask: Task<Void, Never>?

  private var lastCPUTimeMs: Int32 = 0

  private init() {}

  func startLogging() {
    loggingTask?.cancel()
    loggingTask = Task(priority: .background) {
      while !Task.isCancelled {
        logResourceUsage()
        try? await Task.sleep(nanoseconds: 1_000_000_000)
      }
    }
  }

  func stopLogging() {
    loggingTask?.cancel()
  }

  private func logResourceUsage() {
    // Pull memory footprint using iOS kernel APIs
    var info = mach_task_basic_info()
    var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4

    let kerr = withUnsafeMutablePointer(to: &info) {
      $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
        task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
      }
    }

    // CPU Time requires POSIX getrusage to properly accumulate dead Swift Concurrency threads
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)

    let memoryKB = (kerr == KERN_SUCCESS) ? info.resident_size / 1024 : 0
    let currentCPUTimeMs =
      Int32(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) * 1000 + Int32(
        usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1000

    var cpuDeltaMs: Int32 = 0
    if lastCPUTimeMs > 0 {  // Skip the very first tick
      cpuDeltaMs = currentCPUTimeMs - lastCPUTimeMs
    }
    lastCPUTimeMs = currentCPUTimeMs

    // CPU % over the last ~1000ms interval (could exceed 100% on multi-core)
    let cpuPercentage = Double(cpuDeltaMs) / 10.0  // (cpuDeltaMs / 1000.0) * 100
    let formattedCPU = String(format: "%.1f", cpuPercentage)

    print("DEBUG: Logged CPU: \(formattedCPU)% Memory PSS: \(memoryKB)KB")
  }
}
