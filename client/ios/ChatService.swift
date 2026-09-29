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

  private var baseUrl: String {
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
    if self.activeServer == .remote {
      urlString += "?key=\(self.apiKey)"
    }
    guard let url = URL(string: urlString) else { throw URLError(.badURL) }
    request = URLRequest(url: url)
    request.timeoutInterval = 120
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)

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
              let rawJson = try? String(contentsOfFile: path, encoding: .utf8)
            {
              do {
                try self.yieldParsedEvents(from: rawJson, continuation: continuation)
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

    guard let rawJson = String(data: data, encoding: .utf8) else {
      continuation.finish(throwing: Error.noData)
      return
    }

    try self.yieldParsedEvents(from: rawJson, continuation: continuation)
    continuation.finish()
  }

  /// Parses one SSE line. Returns `nil` for lines that carry no JSON event, such as comments,
  /// non-`data:` fields, or malformed payloads.
  nonisolated static func parseSSELine(_ line: String) throws -> [ParsedA2AEvent]? {
    guard line.hasPrefix("data: ") else { return nil }
    let eventString = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
    guard !eventString.isEmpty else { return nil }

    do {
      return try parseResponse(eventString)
    } catch A2AParserError.invalidJSONFormat {
      return nil
    }
  }

  /// Parses one raw A2A response, surfacing JSON-RPC errors as `Error.custom`.
  nonisolated static func parseResponse(_ rawJSON: String) throws -> [ParsedA2AEvent] {
    do {
      return try A2AResponseParser.parse(rawJSON)
    } catch A2AParserError.serverError(_, let message) {
      throw Error.custom(message)
    }
  }

  nonisolated private func yieldParsedEvents(
    from rawJSON: String,
    continuation: AsyncThrowingStream<ParsedA2AEvent, Swift.Error>.Continuation
  ) throws {
    for part in try Self.parseResponse(rawJSON) {
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
