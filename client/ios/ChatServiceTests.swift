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
import XCTest

@testable import A2UI_Example

final class ChatServiceTests: XCTestCase {

  func testParseSSELine_statusUpdateWithText() throws {
    let sseLine = """
      data: {"jsonrpc":"2.0","id":1,"result":{"kind":"status-update","status":{"message":{"role":"agent","parts":[{"text":"Here are top coffee shops in Seattle:"}]}}}}
      """
    let events = try ChatService.parseSSELine(sseLine)
    XCTAssertNotNil(events)
    XCTAssertEqual(events?.count, 1)
    if case .text(let text) = events?.first {
      XCTAssertEqual(text, "Here are top coffee shops in Seattle:")
    } else {
      XCTFail("Expected .text event")
    }
  }

  func testParseSSELine_preservesDataSubstringInsidePayload() throws {
    let sseLine = """
      data: {"jsonrpc":"2.0","id":1,"result":{"kind":"status-update","status":{"message":{"role":"agent","parts":[{"text":"Here is the data: 5 locations found."}]}}}}
      """
    let events = try ChatService.parseSSELine(sseLine)
    XCTAssertNotNil(events)
    if case .text(let text) = events?.first {
      XCTAssertEqual(text, "Here is the data: 5 locations found.")
    } else {
      XCTFail("Expected .text event with preserved 'data: ' substring")
    }
  }

  func testParseSSELine_taskSnapshotWithoutMessageYieldsNoEvents() throws {
    let sseLine = """
      data: {"jsonrpc":"2.0","id":1,"result":{"kind":"task","id":"task-123"}}
      """
    let events = try ChatService.parseSSELine(sseLine)
    XCTAssertEqual(events?.isEmpty, true)
  }

  func testParseSSELine_jsonRpcErrorThrowsCustomError() {
    let sseLine = """
      data: {"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":"Internal error"}}
      """
    XCTAssertThrowsError(try ChatService.parseSSELine(sseLine)) { error in
      XCTAssertEqual(error.localizedDescription, "Internal error")
    }
  }

  func testParseSSELine_ignoresMalformedJSON() throws {
    XCTAssertNil(try ChatService.parseSSELine(#"data: {"jsonrpc":"2.0","#))
  }

  func testParseSSELine_ignoresNonDataLines() throws {
    XCTAssertNil(try ChatService.parseSSELine(""))
    XCTAssertNil(try ChatService.parseSSELine(": keep-alive"))
    XCTAssertNil(try ChatService.parseSSELine("event: message"))
  }

  func testDefaultUseStreamingIsFalse() {
    XCTAssertFalse(ChatService.defaultUseStreaming)
  }

  func testRpcMethod_switchesBetweenStreamAndSend() {
    XCTAssertEqual(ChatService.rpcMethod(useStreaming: true), "message/stream")
    XCTAssertEqual(ChatService.rpcMethod(useStreaming: false), "message/send")
  }

  func testInit_defaultsToRemoteServerWhenOverrideAbsent() async {
    let service = ChatService(environment: [:])
    let activeServer = await service.activeServer
    let baseUrl = await service.baseUrl
    XCTAssertEqual(activeServer, .remote)
    XCTAssertEqual(baseUrl, "REQUIRED_REMOTE_ENDPOINT")
  }

  func testInit_usesDemoLocalhostWhenDefaultServerIsDemo() async {
    let service = ChatService(defaultServer: .demo, environment: [:])
    let activeServer = await service.activeServer
    let baseUrl = await service.baseUrl
    XCTAssertEqual(activeServer, .demo)
    XCTAssertEqual(baseUrl, "http://localhost:10002")
  }

  func testInit_backendURLOverrideForcesDemoServerAndBaseUrl() async {
    let service = ChatService(
      defaultServer: .remote,
      environment: [ChatService.backendURLOverrideKey: "https://maui-backend.google.com"]
    )
    let activeServer = await service.activeServer
    let baseUrl = await service.baseUrl
    XCTAssertEqual(activeServer, .demo)
    XCTAssertEqual(baseUrl, "https://maui-backend.google.com")
  }

  func testInit_ignoresEmptyBackendURLOverride() async {
    let service = ChatService(
      defaultServer: .remote,
      environment: [ChatService.backendURLOverrideKey: ""]
    )
    let activeServer = await service.activeServer
    let baseUrl = await service.baseUrl
    XCTAssertEqual(activeServer, .remote)
    XCTAssertEqual(baseUrl, "REQUIRED_REMOTE_ENDPOINT")
  }

  func testInit_setsBearerTokenFromEnvironmentAndAppliesAuthorizationHeader() async throws {
    let service = ChatService(
      defaultServer: .demo,
      environment: [ChatService.backendBearerTokenKey: "test-identity-token"]
    )
    let bearerToken = await service.bearerToken
    XCTAssertEqual(bearerToken, "test-identity-token")

    var request = URLRequest(url: try XCTUnwrap(URL(string: "https://maui-backend.google.com")))
    await service.applyAuthHeaders(to: &request)
    XCTAssertEqual(
      request.value(forHTTPHeaderField: "Authorization"), "Bearer test-identity-token")
  }

  func testInit_ignoresEmptyBearerTokenInEnvironmentAndOmitsAuthorizationHeader() async throws {
    let service = ChatService(
      defaultServer: .demo,
      environment: [ChatService.backendBearerTokenKey: ""]
    )
    let bearerToken = await service.bearerToken
    XCTAssertNil(bearerToken)

    var request = URLRequest(url: try XCTUnwrap(URL(string: "https://maui-backend.google.com")))
    await service.applyAuthHeaders(to: &request)
    XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
  }

  @MainActor
  func testChatViewModel_streamingChunks_stripsCreateSurfaceAndUpdatesSingleCardInPlace() async {
    let viewModel = ChatViewModel()
    let metadata = ParsedA2AEventMetadata(mimeType: "application/json+a2ui")

    // Chunk 1: initial createSurface + updateComponents
    let chunk1: [Any] = [
      [
        "createSurface": [
          "surfaceId": "surface-1", "catalogId": "a2ui://maps-agentic-ui-catalog.json",
        ]
      ],
      [
        "updateComponents": [
          "surfaceId": "surface-1", "components": [["id": "root", "component": "Column"]],
        ]
      ],
    ]
    viewModel.handleParsedEvent(.data(chunk1, metadata: metadata))
    XCTAssertEqual(viewModel.messages.count, 1)
    let firstMessageId = viewModel.messages.first?.id

    // Chunk 2: incremental updateDataModel
    let chunk2: [Any] = [
      ["updateDataModel": ["surfaceId": "surface-1", "value": ["title": "Pike Place"]]]
    ]
    viewModel.handleParsedEvent(.data(chunk2, metadata: metadata))
    XCTAssertEqual(viewModel.messages.count, 1)
    XCTAssertEqual(viewModel.messages.first?.id, firstMessageId)
    XCTAssertEqual(viewModel.currentUpdateDataModelMsgs.count, 1)

    // Chunk 3 (final summary chunk): re-emits createSurface + updateComponents + updateDataModel
    let chunk3: [Any] = [
      [
        "createSurface": [
          "surfaceId": "surface-1", "catalogId": "a2ui://maps-agentic-ui-catalog.json",
        ]
      ],
      [
        "updateComponents": [
          "surfaceId": "surface-1",
          "components": [
            ["id": "root", "component": "Column"], ["id": "map", "component": "GoogleMap"],
          ],
        ]
      ],
      ["updateDataModel": ["surfaceId": "surface-1", "value": ["title": "Pike Place Market"]]],
    ]
    viewModel.handleParsedEvent(.data(chunk3, metadata: metadata))

    // Still only 1 card message updated in-place, with createSurface omitted
    XCTAssertEqual(viewModel.messages.count, 1)
    XCTAssertEqual(viewModel.messages.first?.id, firstMessageId)
    XCTAssertNotNil(viewModel.currentUpdateComponentsMsg)
    XCTAssertNil(viewModel.currentUpdateComponentsMsg?["createSurface"])
    XCTAssertEqual(viewModel.currentUpdateDataModelMsgs.count, 2)
  }

  func testAgentType_serverPrefix_returnsExpectedRoutingTag() {
    XCTAssertEqual(AgentType.lite.serverPrefix, "[TEMPLATE] ")
    XCTAssertEqual(AgentType.vertex.serverPrefix, "[GROUNDING_TEMPLATE] ")
  }

  #if DEBUG
    func testLaunchOverrides_parsesSettingsAndPrompt() {
      let overrides = LaunchOverrides(environment: [
        LaunchOverrides.useCannedResponsesKey: "True",
        LaunchOverrides.agentTypeKey: "vertex",
        LaunchOverrides.autoSendPromptKey: "Show 3 Indian Restaurants in Seattle",
      ])

      XCTAssertEqual(
        overrides,
        LaunchOverrides(
          useCannedResponses: true, agentType: .vertex,
          autoSendPrompt: "Show 3 Indian Restaurants in Seattle"))
    }

    func testLaunchOverrides_parsesLiteAgentCaseInsensitively() {
      let overrides = LaunchOverrides(environment: [LaunchOverrides.agentTypeKey: "LITE"])

      XCTAssertEqual(overrides.agentType, .lite)
    }

    func testLaunchOverrides_ignoresMissingAndInvalidValues() {
      let overrides = LaunchOverrides(environment: [
        LaunchOverrides.useCannedResponsesKey: "maybe",
        LaunchOverrides.agentTypeKey: "unknown",
        LaunchOverrides.autoSendPromptKey: "   \n ",
      ])

      XCTAssertEqual(overrides, LaunchOverrides())
    }
  #endif
}
