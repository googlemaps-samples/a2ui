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

import GoogleMapsA2UI
import SwiftUI

struct ChatView: View {
  private enum Constants {
    static let scrollDelay: TimeInterval = 0.1
    static let shortAnimationDuration: Double = 0.3
    static let defaultAnimationDuration: Double = 0.5
  }

  @StateObject private var viewModel = ChatViewModel()
  // The text in the input bar.
  @State private var inputText: String = ""
  // The last query submitted to the view model. We need this to be able to revert the input text
  // back to the last query when the user taps on the redo button.
  @State private var lastQuery: String = ""

  var body: some View {
    VStack(spacing: 0) {
      // The Chat History
      ScrollViewReader { proxy in
        ScrollView {
          VStack(spacing: 12) {
            ForEach(viewModel.messages) { message in
              MessageRow(message: message)
                .id(message.id)
            }

            if viewModel.isLoading {
              LoadingBubble()
                .id("loading-indicator")
            }

            // A marker at the very bottom to ensure we can always scroll to the end smoothly.
            Color.clear
              .frame(height: 1)
              .id("bottom-marker")
          }
          .padding(.horizontal)
          .padding(.bottom, 10)
        }
        .onChange(of: viewModel.messages.count) {
          if let lastMessage = viewModel.messages.last {
            DispatchQueue.main.asyncAfter(deadline: .now() + Constants.scrollDelay) {
              switch lastMessage.kind {
              case .text(_, let isUser):
                if isUser {
                  scrollToBottom(proxy: proxy)
                } else {
                  // For agent text, scroll to its top so it's visible
                  withAnimation(.easeOut(duration: Constants.shortAnimationDuration)) {
                    proxy.scrollTo(lastMessage.id, anchor: .top)
                  }
                }
              case .a2uiView:
                break
              }
            }
          }
        }
        .onChange(of: viewModel.webViewToScrollID) { _, id in
          if let id = id {
            DispatchQueue.main.asyncAfter(deadline: .now() + Constants.scrollDelay) {
              withAnimation(.easeInOut(duration: Constants.defaultAnimationDuration)) {
                proxy.scrollTo(id, anchor: .top)
              }
            }
          }
        }
      }

      AgentSelector(selection: $viewModel.selectedAgentType)

      Divider()

      // The Input Bar
      HStack {
        TestCasesMenuView(inputText: $inputText)

        TextField("Message...", text: $inputText, axis: .vertical)
          .textFieldStyle(RoundedBorderTextFieldStyle())
          .lineLimit(1...8)
          .foregroundColor(.primary)
          .padding(.vertical, 8)
          .onSubmit {  // Submit when the user presses the return key
            submitMessage()
          }

        if !lastQuery.isEmpty && inputText.isEmpty {
          Button(action: {
            inputText = lastQuery
          }) {
            Image(systemName: "arrow.uturn.backward")
              .foregroundColor(.white)
              .padding(10)
              .background(Color.gray)
              .clipShape(Circle())
          }
        }

        Button(action: submitMessage) {
          Image(systemName: "paperplane.fill")
            .foregroundColor(.white)
            .padding(10)
            .background(Color.blue)
            .clipShape(Circle())
        }
      }
      .padding(.horizontal)
      .padding(.bottom, 8)
    }
  }

  /// Submits the current input text to the view model.
  private func submitMessage() {
    let text = inputText.trimmingCharacters(in: .whitespaces)
    guard !text.isEmpty else { return }
    viewModel.sendMessage(text: text)
    lastQuery = text
    inputText = ""
  }

  /// Smoothly scrolls the list to the very bottom marker.
  ///
  /// - Parameter proxy: The `ScrollViewProxy` used to control scroll positioning.
  private func scrollToBottom(proxy: ScrollViewProxy) {
    withAnimation(.easeOut(duration: Constants.shortAnimationDuration)) {
      proxy.scrollTo("bottom-marker", anchor: .bottom)
    }
  }
}

// Layout for individual chat bubbles
struct MessageRow: View {
  let message: ChatMessage

  var body: some View {
    HStack {
      switch message.kind {
      case .text(let content, let isUser):
        if isUser { Spacer() }

        Text(content)
          .padding(12)
          .background(isUser ? Color.blue : Color(UIColor.systemGray5))
          .foregroundColor(isUser ? .white : .primary)
          .clipShape(ChatBubbleShape(isUser: isUser))

        if !isUser { Spacer() }

      case .a2uiView(_, let view):
        view
      }
    }
  }
}

// Custom shape for chat bubble corners
struct ChatBubbleShape: Shape {
  let isUser: Bool

  /// Calculates the rounded corner path based on the message sender.
  ///
  /// - Parameter rect: The bounding rectangle of the bubble.
  /// - Returns: A `Path` describing the custom shape.
  func path(in rect: CGRect) -> Path {
    let path = UIBezierPath(
      roundedRect: rect,
      byRoundingCorners: [
        .topLeft,
        .topRight,
        isUser ? .bottomLeft : .bottomRight,  // Flat corner on the sender's side
      ],
      cornerRadii: CGSize(width: 16, height: 16)
    )
    return Path(path.cgPath)
  }
}

struct LoadingBubble: View {
  var body: some View {
    HStack {
      HStack(spacing: 8) {
        ProgressView()
          .progressViewStyle(CircularProgressViewStyle())
        Text("Agent is thinking...")
          .font(.subheadline)
          .foregroundColor(.secondary)
      }
      .padding(12)
      .background(Color(UIColor.systemGray6))
      .clipShape(ChatBubbleShape(isUser: false))

      Spacer()
    }
  }
}

struct AgentSelector: View {
  @Binding var selection: AgentType

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      ForEach(AgentType.allCases) { type in
        Button(action: {
          withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            selection = type
          }
        }) {
          HStack(spacing: 12) {
            Image(systemName: selection == type ? "dot.circle.fill" : "circle")
              .foregroundColor(selection == type ? .blue : .secondary)
              .font(.title3)

            Text(type.rawValue)
              .font(.body)
              .foregroundColor(.primary)

            Spacer()
          }
          .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
      }
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 8)
  }
}
