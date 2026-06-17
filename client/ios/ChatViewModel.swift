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

import Combine
import Foundation
import SwiftUI

import GoogleMapsA2UI

class ChatViewModel: ObservableObject {
  @Published var messages: [ChatMessage] = []
  @Published var isLoading: Bool = false
  @Published var webViewToScrollID: UUID?
  @Published var selectedGroundingType: GroundingType = .lite
  private let googleMapsApiKey = "YOUR_API_KEY"

  init() {
    A2UIServices.provideApiKey(googleMapsApiKey)
  }


  enum ServerType {
    case demo  // Port 10002 (Internal)
    case remote // Remote Gateway
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

  private var appName: String {
    switch activeServer {
    case .demo: return "restaurant_finder"
    case .remote: return "restaurant_finder"
    }
  }

  private var activeSessionID: String?
  private var useSSEProtocol = false
  private let contextID = UUID().uuidString

  /// Sends a text message to the server.
  ///
  /// - Parameter text: The message string to send.
  func sendMessage(text: String) {
    var serverText = text
    if selectedGroundingType == .vertex {
      serverText = "[GROUNDING] \(text)"
    }
    let payload: [String: Any] = ["text": serverText]
    addMessage(.text(content: text, isUser: true))
    callPythonServer(userMessage: payload)
  }

  /// Sends a structured action (like 'get_directions') to the server.
  ///
  /// - Parameter jsonString: A JSON string containing the action context.
  func sendAction(jsonString: String) {

    guard let data = jsonString.data(using: .utf8),
      let contextDict = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    else {
      print("Failed to parse action JSON")
      return
    }

    let payload: [String: Any] = [
      "userAction": [
        "name": "get_directions",
        "context": contextDict,
      ]
    ]
    callPythonServer(userMessage: payload)
  }

  /// Discovers the underlying protocol (SSE vs JSON-RPC) required to communicate with the server.
  ///
  /// - Parameter completion: A closure called once the protocol discovery is complete.
  private func discoverProtocol(completion: @escaping () -> Void) {
    // If we already have a session, we've already discovered the protocol
    if activeSessionID != nil || !useSSEProtocol && activeSessionID != nil {
      completion()
      return
    }

    // Attempt to create a session (ADK Web Server Handshake)
    var urlString = "\(baseUrl)/apps/\(appName)/users/user/sessions"
    if activeServer == .remote {
      urlString += "?key=\(apiKey)"
    }
    guard let url = URL(string: urlString) else {
      completion()
      return
    }
    var request = URLRequest(url: url)
    request.timeoutInterval = 120
    request.httpMethod = "POST"

    if activeServer == .remote {
      request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
    }

    URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
      let httpResponse = response as? HTTPURLResponse
      
      if let http = httpResponse {
          print("DEBUG: Handshake HTTP Status: \(http.statusCode)")
          if let data = data, let body = String(data: data, encoding: .utf8) {
              print("DEBUG: Handshake Response Body: \(body)")
          }
      }

      if let error = error {
          print("DEBUG: Handshake Error: \(error.localizedDescription)")
      }

      if httpResponse?.statusCode == 200, let data = data,
        let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
        let id = json["id"] as? String
      {
        DispatchQueue.main.async {
          self?.activeSessionID = id
          self?.useSSEProtocol = true
          print("DEBUG: Discovered ADK Web Server (SSE) protocol")
          completion()
        }
      } else {
        DispatchQueue.main.async {
          self?.useSSEProtocol = false
          print("DEBUG: Discovered Standalone (JSON-RPC) protocol")
          completion()
        }
      }
    }.resume()
  }

  /// Calls the Python backend server with the provided user message payload.
  ///
  /// - Parameter userMessage: A dictionary containing the message or action to send.
  private func callPythonServer(userMessage: [String: Any]) {
    isLoading = true

    discoverProtocol { [weak self] in
      guard let self = self else { return }

      var parts: [[String: Any]] = []
      if let text = userMessage["text"] as? String {
        parts.append(["text": text])
      } else if let action = userMessage["userAction"] as? [String: Any] {
        parts.append(["data": ["userAction": action]])
      }

      var request: URLRequest
      if self.useSSEProtocol {
        // SSE Protocol for ADK Web Server
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
        guard let url = URL(string: urlString) else { return }
        request = URLRequest(url: url)
        request.timeoutInterval = 120
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
      } else {
        // JSON-RPC Protocol for Standalone Server
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
        guard let url = URL(string: urlString) else { return }
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

      URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
        DispatchQueue.main.async {
          guard let self = self else { return }
          self.isLoading = false

          if let httpResponse = response as? HTTPURLResponse {
            print("DEBUG: HTTP Status Code: \(httpResponse.statusCode)")
            print("DEBUG: Response Headers: \(httpResponse.allHeaderFields)")
          }

          guard let data = data, error == nil else {
            print("DEBUG: Network Error: \(error?.localizedDescription ?? "Unknown")")
            self.addMessage(.text(content: "Network Error: \(error?.localizedDescription ?? "Unknown")", isUser: false))
            return
          }

          let responseString = String(data: data, encoding: .utf8) ?? ""
          print("DEBUG: Raw Server Response: \(responseString)")


          if self.useSSEProtocol || responseString.contains("data: ") {
            // Handle SSE Stream
            let events = responseString.components(separatedBy: "\n")
              .filter { $0.hasPrefix("data: ") }
              .map { $0.replacingOccurrences(of: "data: ", with: "") }

            for event in events {
              if let eventData = event.data(using: .utf8),
                let rawJson = try? JSONSerialization.jsonObject(with: eventData) as? [String: Any] {
                self.processJsonResponse(rawJson)
              }
            }
          } else {
            // Handle Single JSON response
            if let rawJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
              if let result = rawJson["result"] as? [String: Any] {
                self.processJsonResponse(result)
              } else {
                self.processJsonResponse(rawJson)
              }
            }
          }
        }
      }.resume()
    }
  }

  /// Processes a single JSON response from the server, adding messages to the timeline.
  ///
  /// - Parameter json: The parsed JSON dictionary from the server.
  private func processJsonResponse(_ json: [String: Any]) {
    if let errorDict = json["error"] as? [String: Any],
       let errorMessage = errorDict["message"] as? String {
      self.addMessage(.text(content: "Server Error: \(errorMessage)", isUser: false))
      return
    } else if let errorObj = json["error"] {
      let errorString = (errorObj is NSNull) ? "Unknown error" : String(describing: errorObj)
      self.addMessage(.text(content: "Server Error: \(errorString)", isUser: false))
      return
    }

    do {
      let parts = try A2AResponseParser.parse(json)
      for part in parts {
        switch part {
        case .data(_, let metadata):
          if metadata?.mimeType == "application/json+a2ui" {
            let messageId = UUID()
            let view = A2UIView(
              part: part,
              id: messageId.uuidString,
              onUserAction: { [weak self] actionStr in
                self?.sendAction(jsonString: actionStr)
              },
              onRenderComplete: { [weak self] id, latency, status in
                if let uuid = UUID(uuidString: id) {
                  DispatchQueue.main.async {
                    self?.webViewToScrollID = uuid
                  }
                }
              }
            )
            let message = ChatMessage(id: messageId, kind: .a2uiView(type: "GoogleMapsA2UI", view: AnyView(view)))
            self.addMessage(message)
          }
        case .text(let text):
          if !text.isEmpty {
            self.addMessage(.text(content: text, isUser: false))
          }
        }
      }
    } catch {
      print("Error parsing response: \(error)")
      self.addMessage(.text(content: "Parsing Error: \(error.localizedDescription)", isUser: false))
    }
  }


  /// Adds a message to the local chat message list.
  ///
  /// - Parameter msg: The `ChatMessage` to append to the conversation.
  private func addMessage(_ msg: ChatMessage) {
    self.messages.append(msg)
  }
}
