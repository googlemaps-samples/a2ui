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
  func sendMessage(text: String, agentType: AgentType, useStreaming: Bool) async throws
    -> AsyncThrowingStream<
      ParsedA2AEvent, Swift.Error
    >
  func sendAction(jsonString: String, useStreaming: Bool) async throws -> AsyncThrowingStream<
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
  nonisolated static let defaultUseStreaming: Bool = true
  private let activeServer: ServerType = .remote
  private let remoteEndpoint = "REQUIRED_REMOTE_ENDPOINT"
  private let apiKey = "REQUIRED_REMOTE_API_KEY"
  // ---------------------

  /// Launch environment key that points the app at a different demo (`.demo` protocol) server.
  ///
  /// UI tests run out of process, so they set this via `XCUIApplication.launchEnvironment`. When
  /// present it takes precedence over `activeServer`.
  nonisolated static let backendUrlOverrideKey = "A2UI_BACKEND_URL_OVERRIDE"

  private let backendUrlOverride: String? = ProcessInfo.processInfo.environment[
    ChatService.backendUrlOverrideKey]

  /// The server type actually in use, accounting for `backendUrlOverride`.
  private var serverType: ServerType {
    backendUrlOverride == nil ? activeServer : .demo
  }

  private var baseUrl: String {
    if let backendUrlOverride {
      return backendUrlOverride
    }
    switch activeServer {
    case .demo: return "http://localhost:10002"
    case .remote: return remoteEndpoint
    }
  }

  nonisolated static func rpcMethod(useStreaming: Bool) -> String {
    return useStreaming ? "message/stream" : "message/send"
  }

  private let appName = "restaurant_finder"

  private var activeSessionID: String?
  private let contextID = UUID().uuidString

  func sendMessage(
    text: String,
    agentType: AgentType,
    useStreaming: Bool = ChatService.defaultUseStreaming
  ) async throws
    -> AsyncThrowingStream<
      ParsedA2AEvent, Swift.Error
    >
  {
    var serverText = text
    switch agentType {
    case .vertex:
      serverText = "[GROUNDING] \(text)"
    case .template:
      serverText = "[TEMPLATE] \(text)"
    case .lite:
      serverText = "[MCP] \(text)"
    }
    let payload: [String: Any] = ["text": serverText]
    return try await callPythonServer(userMessage: payload, useStreaming: useStreaming)
  }

  func sendAction(
    jsonString: String,
    useStreaming: Bool = ChatService.defaultUseStreaming
  ) async throws -> AsyncThrowingStream<
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
    return try await callPythonServer(userMessage: payload, useStreaming: useStreaming)
  }

  private func initializeSession() async throws {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UI_TEST_MOCK_SCENARIO"] != nil { return }
      if UserDefaults.standard.bool(forKey: MockScenarioRegistry.useCannedResponsesKey) { return }
    #endif

    if activeSessionID != nil {
      return
    }

    var urlString = "\(baseUrl)/apps/\(appName)/users/user/sessions"
    if serverType == .remote {
      urlString += "?key=\(apiKey)"
    }
    guard let url = URL(string: urlString) else {
      throw URLError(.badURL)
    }

    var request = URLRequest(url: url)
    request.timeoutInterval = 120
    request.httpMethod = "POST"
    if serverType == .remote {
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
    }
  }

  private func callPythonServer(
    userMessage: [String: Any],
    useStreaming: Bool
  ) async throws -> AsyncThrowingStream<
    ParsedA2AEvent, Swift.Error
  > {
    try await initializeSession()

    var parts: [[String: Any]] = []
    if let text = userMessage["text"] as? String {
      parts.append(["text": text])
    } else if let action = userMessage["userAction"] as? [String: Any] {
      parts.append(["data": ["userAction": action]])
    }

    var request: URLRequest
    let body: [String: Any] = [
      "jsonrpc": "2.0",
      "method": Self.rpcMethod(useStreaming: useStreaming),
      "id": 1,
      "sessionId": self.activeSessionID ?? "",
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
    if self.serverType == .remote {
      urlString += "?key=\(self.apiKey)"
    }
    guard let url = URL(string: urlString) else { throw URLError(.badURL) }
    request = URLRequest(url: url)
    request.timeoutInterval = 120
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)

    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")

    if self.serverType == .remote {
      request.setValue(self.apiKey, forHTTPHeaderField: "x-api-key")
    }

    if self.serverType == .demo {
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
      let fetchTask = Task {
        do {
          if useStreaming {
            try await self.performStreamingRequest(
              request: request, startTime: startTime, continuation: continuation)
          } else {
            try await self.performNonStreamingRequest(
              request: request, startTime: startTime, continuation: continuation)
          }
        } catch {
          LatencyLogger.shared.logLatency(
            type: .server, latencyMs: Int(Date().timeIntervalSince(startTime) * 1000),
            status: "ERROR")
          continuation.finish(throwing: error)
        }
      }

      continuation.onTermination = { @Sendable _ in
        fetchTask.cancel()
      }
    }
  }

  private func validateHTTPResponse(
    _ response: URLResponse,
    startTime: Date,
    continuation: AsyncThrowingStream<ParsedA2AEvent, Swift.Error>.Continuation
  ) -> Bool {
    let latencyMs = Int(Date().timeIntervalSince(startTime) * 1000)

    guard let httpResponse = response as? HTTPURLResponse else {
      LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "ERROR")
      continuation.finish(throwing: Error.custom("Invalid HTTP response"))
      return false
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "ERROR")
      continuation.finish(
        throwing: Error.custom("HTTP Error: status code \(httpResponse.statusCode)"))
      return false
    }

    LatencyLogger.shared.logLatency(type: .server, latencyMs: latencyMs, status: "SUCCESS")
    return true
  }

  private func performStreamingRequest(
    request: URLRequest,
    startTime: Date,
    continuation: AsyncThrowingStream<ParsedA2AEvent, Swift.Error>.Continuation
  ) async throws {
    let (bytes, response) = try await URLSession.shared.bytes(for: request)
    guard validateHTTPResponse(response, startTime: startTime, continuation: continuation) else {
      return
    }

    for try await line in bytes.lines {
      do {
        if let parsedParts = try Self.parseSSELine(line) {
          for part in parsedParts {
            continuation.yield(part)
          }
        }
      } catch {
        continuation.finish(throwing: error)
        return
      }
    }

    continuation.finish()
  }

  private func performNonStreamingRequest(
    request: URLRequest,
    startTime: Date,
    continuation: AsyncThrowingStream<ParsedA2AEvent, Swift.Error>.Continuation
  ) async throws {
    let (data, response) = try await URLSession.shared.data(for: request)
    guard validateHTTPResponse(response, startTime: startTime, continuation: continuation) else {
      return
    }

    guard let rawJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    else {
      continuation.finish(throwing: Error.noData)
      return
    }

    let payload = (rawJson["result"] as? [String: Any]) ?? rawJson
    try self.processParsedJSON(payload, continuation: continuation)
    continuation.finish()
  }

  nonisolated static func parseSSELine(_ line: String) throws -> [ParsedA2AEvent]? {
    guard line.hasPrefix("data: ") else { return nil }
    let eventString = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
    guard !eventString.isEmpty else { return nil }

    guard let eventData = eventString.data(using: .utf8),
      let rawJson = try? JSONSerialization.jsonObject(with: eventData) as? [String: Any]
    else {
      return nil
    }

    let payload = (rawJson["result"] as? [String: Any]) ?? rawJson

    let kind = payload["kind"] as? String
    if kind == "task" {
      return nil
    }

    var finalPayloadToParse = payload
    if kind == "status-update",
      let status = payload["status"] as? [String: Any],
      let message = status["message"] as? [String: Any]
    {
      finalPayloadToParse = message
    }

    if let errorDict = finalPayloadToParse["error"] as? [String: Any],
      let errorMessage = errorDict["message"] as? String
    {
      throw Error.custom(errorMessage)
    } else if let errorObj = finalPayloadToParse["error"], !(errorObj is NSNull) {
      throw Error.custom(String(describing: errorObj))
    }

    return try A2AResponseParser.parse(finalPayloadToParse)
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
    let strippedText =
      text
      .replacingOccurrences(of: "[GROUNDING] ", with: "")
      .replacingOccurrences(of: "[TEMPLATE] ", with: "")
      .replacingOccurrences(of: "[MCP] ", with: "")
    return MockScenarioRegistry.scenarios.first(where: { $0.query == strippedText })?.fileName
  }
}
