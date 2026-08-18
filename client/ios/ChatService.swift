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
import GoogleMapsA2UI

protocol ChatServiceProtocol {
  func sendMessage(text: String, isVertex: Bool) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  >
  func sendAction(jsonString: String) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  >
}

actor ChatService: ChatServiceProtocol {
  enum ServerType {
    case demo  // Port 10002 (Internal)
    case remote  // Remote Gateway
  }

  enum Error: Swift.Error, LocalizedError {
    case failedToParseActionJSON
    case mockJSONNotFound
    case noData
    case custom(String)

    var errorDescription: String? {
      switch self {
      case .failedToParseActionJSON: return "Failed to parse action JSON"
      case .mockJSONNotFound: return "Mock JSON not found"
      case .noData: return "No data"
      case .custom(let message): return message
      }
    }
  }

  // --- CONFIGURATION ---
  private let activeServer: ServerType = .remote
  private let remoteEndpoint = "REQUIRED_REMOTE_ENDPOINT"
  private let apiKey = "REQUIRED_REMOTE_API_KEY"
  // ---------------------

  private var baseUrl: String {
    switch activeServer {
    case .demo: return "http://localhost:10002"
    case .remote: return remoteEndpoint
    }
  }

  private let appName = "restaurant_finder"

  private var activeSessionID: String?
  private var useSSEProtocol = false
  private let contextID = UUID().uuidString

  func sendMessage(text: String, isVertex: Bool) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  > {
    var serverText = text
    if isVertex {
      serverText = "[GROUNDING] \(text)"
    }
    let payload: [String: Any] = ["text": serverText]
    return try await callPythonServer(userMessage: payload)
  }

  func sendAction(jsonString: String) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  > {
    guard let data = jsonString.data(using: .utf8),
      let contextDict = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    else {
      throw Error.failedToParseActionJSON
    }

    let payload: [String: Any] = [
      "userAction": [
        "name": "get_directions",
        "context": contextDict,
      ]
    ]
    return try await callPythonServer(userMessage: payload)
  }

  private func discoverProtocol() async throws {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UI_TEST_MOCK_SCENARIO"] != nil { return }
      if UserDefaults.standard.bool(forKey: MockScenarioRegistry.useCannedResponsesKey) { return }
    #endif

    if activeSessionID != nil || (!useSSEProtocol && activeSessionID != nil) {
      return
    }

    var urlString = "\(baseUrl)/apps/\(appName)/users/user/sessions"
    if activeServer == .remote {
      urlString += "?key=\(apiKey)"
    }
    guard let url = URL(string: urlString) else {
      throw URLError(.badURL)
    }

    var request = URLRequest(url: url)
    request.timeoutInterval = 120
    request.httpMethod = "POST"
    if activeServer == .remote {
      request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
    }

    // Attempt handshake
    let (data, response) = try await URLSession.shared.data(for: request)
    let httpResponse = response as? HTTPURLResponse

    if httpResponse?.statusCode == 200,
      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      let id = json["id"] as? String
    {
      self.activeSessionID = id
      self.useSSEProtocol = true
    } else {
      self.useSSEProtocol = false
    }
  }

  private func callPythonServer(userMessage: [String: Any]) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  > {
    try await discoverProtocol()

    var parts: [[String: Any]] = []
    if let text = userMessage["text"] as? String {
      parts.append(["text": text])
    } else if let action = userMessage["userAction"] as? [String: Any] {
      parts.append(["data": ["userAction": action]])
    }

    var request: URLRequest
    if self.useSSEProtocol {
      let body: [String: Any] = [
        "appName": self.appName,
        "userId": "user",
        "sessionId": self.activeSessionID ?? "",
        "newMessage": [
          "role": "user",
          "parts": parts,
        ],
      ]
      var urlString = "\(self.baseUrl)/run_sse"
      if self.activeServer == .remote {
        urlString += "?key=\(self.apiKey)"
      }
      guard let url = URL(string: urlString) else { throw URLError(.badURL) }
      request = URLRequest(url: url)
      request.timeoutInterval = 120
      request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    } else {
      let body: [String: Any] = [
        "jsonrpc": "2.0",
        "method": "message/send",
        "id": 1,
        "params": [
          "message": [
            "role": "user",
            "messageId": UUID().uuidString,
            "contextId": self.contextID,
            "parts": parts,
          ]
        ],
      ]
      var urlString = self.baseUrl
      if self.activeServer == .remote {
        urlString += "?key=\(self.apiKey)"
      }
      guard let url = URL(string: urlString) else { throw URLError(.badURL) }
      request = URLRequest(url: url)
      request.timeoutInterval = 120
      request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    }

    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")

    if self.activeServer == .remote {
      request.setValue(self.apiKey, forHTTPHeaderField: "x-api-key")
    }

    if self.activeServer == .demo {
      request.setValue(
        "https://a2ui.org/a2a-extension/a2ui/v0.9", forHTTPHeaderField: "X-A2A-Extensions")
    }

    return AsyncThrowingStream { continuation in
      #if DEBUG
        var resolvedMockScenario: String? = ProcessInfo.processInfo.environment[
          "UI_TEST_MOCK_SCENARIO"]

        if resolvedMockScenario == nil {
          resolvedMockScenario = getCannedResponseMockScenario(from: userMessage)
        }

        if let mockScenario = resolvedMockScenario {
          // Hermetic UI Testing Mock Interception
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self = self else { return }
            if let path = Bundle.main.path(forResource: mockScenario, ofType: "json"),
              let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let rawJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            {
              do {
                if let result = rawJson["result"] as? [String: Any] {
                  try self.processParsedJSON(result, continuation: continuation)
                } else {
                  try self.processParsedJSON(rawJson, continuation: continuation)
                }
                continuation.finish()
              } catch {
                continuation.finish(throwing: error)
              }
            } else {
              continuation.finish(throwing: Error.mockJSONNotFound)
            }
          }
          return
        }
      #endif

      let startTime = Date()
      let isSSE = self.useSSEProtocol
      let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
        let latencyMs = Int(Date().timeIntervalSince(startTime) * 1000)
        guard let self = self else { return }

        if let error = error {
          LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "ERROR")
          continuation.finish(throwing: error)
          return
        }

        guard let data = data else {
          LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "ERROR")
          continuation.finish(throwing: Error.noData)
          return
        }

        LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "SUCCESS")

        let responseString = String(data: data, encoding: .utf8) ?? ""
        if isSSE || responseString.contains("data: ") {
          let events = responseString.components(separatedBy: "\n")
            .filter { $0.hasPrefix("data: ") }
            .map { $0.replacingOccurrences(of: "data: ", with: "") }

          for event in events {
            if let eventData = event.data(using: .utf8),
              let rawJson = try? JSONSerialization.jsonObject(with: eventData) as? [String: Any]
            {

              do {
                try self.processParsedJSON(rawJson, continuation: continuation)
              } catch {
                continuation.finish(throwing: error)
                return
              }
            }
          }
        } else {
          if let rawJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            let payload = (rawJson["result"] as? [String: Any]) ?? rawJson

            do {
              try self.processParsedJSON(payload, continuation: continuation)
            } catch {
              continuation.finish(throwing: error)
              return
            }
          }
        }
        continuation.finish()
      }
      task.resume()

      continuation.onTermination = { @Sendable _ in
        task.cancel()
      }
    }
  }

  nonisolated private func processParsedJSON(
    _ dictionary: [String: Any],
    continuation: AsyncThrowingStream<ParsedA2AEvent, Swift.Error>.Continuation
  ) throws {
    if let errorDict = dictionary["error"] as? [String: Any],
      let errorMessage = errorDict["message"] as? String
    {
      throw Error.custom(errorMessage)
    } else if let errorObj = dictionary["error"], !(errorObj is NSNull) {
      throw Error.custom(String(describing: errorObj))
    }

    let parsedParts = try A2AResponseParser.parse(dictionary)
    for part in parsedParts {
      continuation.yield(part)
    }
  }

  private func getCannedResponseMockScenario(from userMessage: [String: Any]) -> String? {
    guard UserDefaults.standard.bool(forKey: MockScenarioRegistry.useCannedResponsesKey),
      let text = userMessage["text"] as? String
    else {
      return nil
    }
    let strippedText = text.replacingOccurrences(of: "[GROUNDING] ", with: "")
    return MockScenarioRegistry.scenarios.first(where: { $0.query == strippedText })?.fileName
  }
}
