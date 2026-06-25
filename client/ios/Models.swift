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

import Foundation
import SwiftUI

enum GroundingType: String, CaseIterable, Identifiable {
  case lite = "Grounding Lite (MCP)"
  case vertex = "Grounding with Google Maps (Vertex)"

  var id: Self { self }
}

struct ChatMessage: Identifiable {
  var id = UUID()

  enum Kind {
    case text(content: String, isUser: Bool)
    case a2uiView(type: String, view: AnyView)
  }

  var kind: Kind

  /// Creates a text-based chat message.
  ///
  /// - Parameters:
  ///   - content: The text content of the message.
  ///   - isUser: A boolean indicating whether the message is from the user (`true`) or the agent (`false`).
  /// - Returns: A new `ChatMessage` instance.
  static func text(content: String, isUser: Bool) -> ChatMessage {
    return ChatMessage(kind: .text(content: content, isUser: isUser))
  }

  /// Creates an A2UI view-based chat message.
  ///
  /// - Parameters:
  ///   - type: A string identifier for the A2UI type.
  ///   - view: The underlying SwiftUI `AnyView` representing the rendered A2UI.
  /// - Returns: A new `ChatMessage` instance.
  static func a2uiView(type: String, _ view: AnyView) -> ChatMessage {
    return ChatMessage(kind: .a2uiView(type: type, view: view))
  }
}


