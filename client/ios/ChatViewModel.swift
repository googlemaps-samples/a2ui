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

import Combine
import Foundation
import GoogleMapsA2UI
import SwiftUI

@MainActor
class ChatViewModel: ObservableObject {
  @Published private(set) var messages: [ChatMessage] = []
  @Published private(set) var isLoading: Bool = false
  @Published var webViewToScrollID: UUID?
  @Published var selectedAgentType: AgentType = .template
  @Published var useStreaming: Bool = ChatService.defaultUseStreaming

  private let chatService: ChatServiceProtocol
  private let googleMapsApiKey = "$GOOGLE_MAPS_API_KEY"
  // Tracks the message ID of the currently streaming A2UI card so subsequent chunks update it in-place.
  private var currentStreamingA2UIMessageId: UUID? = nil
  // Tracks the message ID of the currently streaming text bubble so text updates in-place.
  private var currentStreamingTextMessageId: UUID? = nil

  init(chatService: ChatServiceProtocol = ChatService()) {
    self.chatService = chatService
    A2UIServices.provideApiKey(googleMapsApiKey)
    Task {
      await ResourceLogger.shared.startLogging()
    }
  }

  /// Sends a text message to the server.
  func sendMessage(text: String) {
    let agentType = selectedAgentType
    addMessage(.text(content: text, isUser: true))
    currentStreamingA2UIMessageId = nil
    currentStreamingTextMessageId = nil

    Task {
      isLoading = true
      do {
        let stream = try await chatService.sendMessage(
          text: text, agentType: agentType, useStreaming: useStreaming)
        for try await part in stream {
          handleParsedEvent(part)
        }
      } catch {
        addMessage(.text(content: "Error: \(error.localizedDescription)", isUser: false))
      }
      isLoading = false
    }
  }

  /// Sends a structured action to the server.
  func sendAction(jsonString: String) {
    currentStreamingA2UIMessageId = nil
    currentStreamingTextMessageId = nil
    Task {
      do {
        let stream = try await chatService.sendAction(
          jsonString: jsonString, useStreaming: useStreaming)
        for try await part in stream {
          handleParsedEvent(part)
        }
      } catch {
        addMessage(
          .text(content: "Error processing action: \(error.localizedDescription)", isUser: false))
      }
    }
  }

  private func handleParsedEvent(_ part: ParsedA2AEvent) {
    switch part {
    case .data(let payloadDict, let metadata):
      if metadata?.mimeType == "application/json+a2ui" {
        // Intercept transient status indicator (tool call status UI deferred)
        if let dict = payloadDict as? [String: Any],
          let updateDataModel = dict["updateDataModel"] as? [String: Any],
          let surfaceId = updateDataModel["surfaceId"] as? String,
          surfaceId == "dummy_status"
        {
          return
        }

        let isUpdate: Bool
        let messageId: UUID
        if let existingId = self.currentStreamingA2UIMessageId {
          messageId = existingId
          isUpdate = true
        } else {
          messageId = UUID()
          self.currentStreamingA2UIMessageId = messageId
          isUpdate = false
        }

        let renderStartTime = Date()
        let view = A2UIView(
          part: part,
          id: messageId.uuidString,
          onUserAction: { [weak self] actionStr in
            Task { @MainActor in
              self?.sendAction(jsonString: actionStr)
            }
          },
          onRenderComplete: { [weak self] id, _, status in
            let actualLatencyMs = Int(Date().timeIntervalSince(renderStartTime) * 1000)
            LatencyLogger.shared.logLatency(
              type: .render, latencyMs: actualLatencyMs, status: status)
            if let uuid = UUID(uuidString: id) {
              Task { @MainActor in
                self?.webViewToScrollID = uuid
              }
            }
          }
        )
        let message = ChatMessage(
          id: messageId, kind: .a2uiView(type: "GoogleMapsA2UI", view: AnyView(view)))

        if isUpdate, let index = self.messages.firstIndex(where: { $0.id == messageId }) {
          self.messages[index] = message
        } else {
          addMessage(message)
        }
      }
    case .text(let text):
      // Filter out pure whitespace/newline chunks to prevent empty message boxes
      if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        if let existingId = self.currentStreamingTextMessageId,
          let index = self.messages.firstIndex(where: { $0.id == existingId })
        {
          self.messages[index] = ChatMessage(
            id: existingId, kind: .text(content: text, isUser: false))
        } else {
          let newId = UUID()
          self.currentStreamingTextMessageId = newId
          addMessage(ChatMessage(id: newId, kind: .text(content: text, isUser: false)))
        }
      }
    }
  }

  private func addMessage(_ msg: ChatMessage) {
    self.messages.append(msg)
  }
}
