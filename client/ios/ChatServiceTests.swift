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

@testable import A2UIExampleApp_lib

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

  func testParseSSELine_ignoresTaskMetadataKind() throws {
    let sseLine = """
      data: {"jsonrpc":"2.0","id":1,"result":{"kind":"task","id":"task-123"}}
      """
    let events = try ChatService.parseSSELine(sseLine)
    XCTAssertNil(events)
  }

  func testParseSSELine_ignoresNonDataLines() throws {
    XCTAssertNil(try ChatService.parseSSELine(""))
    XCTAssertNil(try ChatService.parseSSELine(": keep-alive"))
    XCTAssertNil(try ChatService.parseSSELine("event: message"))
  }

  func testDefaultUseStreamingIsTrue() {
    XCTAssertTrue(ChatService.defaultUseStreaming)
  }

  func testRpcMethod_switchesBetweenStreamAndSend() {
    XCTAssertEqual(ChatService.rpcMethod(useStreaming: true), "message/stream")
    XCTAssertEqual(ChatService.rpcMethod(useStreaming: false), "message/send")
  }
}
