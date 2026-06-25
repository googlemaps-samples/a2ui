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

import XCTest

final class A2UIExampleUITests: XCTestCase {

  let app = XCUIApplication()

  override func setUpWithError() throws {
    continueAfterFailure = false
    // The application is relaunched for each testcase. This precludes us from having to clean
    // up application state each time.
    app.launch()
  }

  func testSeattleCoffeeShops() throws {
    try runTestCase(named: "Seattle Coffee Shops")
  }

  func testMVGoogleGyms() throws {
    try runTestCase(named: "MV Google Gyms")
  }

  func testEdgewaterHotel() throws {
    try runTestCase(named: "Edgewater Hotel")
  }

  func testGasWorksPark() throws {
    try runTestCase(named: "Gas Works Park")
  }

  func testKirklandCommute() throws {
    try runTestCase(named: "Kirkland Commute")
  }

  func testLePetiteAcademy() throws {
    try runTestCase(named: "Le Petite Academy")
  }

  func testNYCAttractions() throws {
    try runTestCase(named: "NYC Attractions")
  }

  func testSLUSaladsVegan() throws {
    try runTestCase(named: "SLU Salads (Vegan)")
  }

  func testSLUSaladsClick() throws {
    try runTestCase(named: "SLU Salads (Click)")
  }

  func testSLUSaladsDirections() throws {
    try runTestCase(named: "SLU Salads (Directions)")
  }

  func testLondonItinerary() throws {
    try runTestCase(named: "London Itinerary")
  }

  // MARK: - Helper Methods

  /// Runs a specified UI test case by simulating user interaction.
  ///
  /// - Parameter testCaseName: The name of the test case to run, matching the button label.
  /// - Throws: An error if an expectation times out or fails.
  private func runTestCase(named testCaseName: String) throws {
    // Tap the flask icon to open the TestCases menu
    app.buttons["flask.fill"].tap()

    // Tap the specific test case
    app.buttons[testCaseName].firstMatch.tap()

    // Tap the send button (paperplane.fill)
    app.buttons["paperplane.fill"].tap()

    // Wait for the web view to appear, indicating an A2UI response
    let webView = app.webViews.element
    let exists = NSPredicate(format: "exists == true")
    expectation(for: exists, evaluatedWith: webView, handler: nil)

    // Use a longer timeout as agent responses can take time
    waitForExpectations(timeout: 30, handler: nil)

    XCTAssertTrue(webView.exists, "Web view should exist for test case: \(testCaseName)")

    // Ensure the web view contains some text content
    let webViewHasContent = NSPredicate(format: "staticTexts.count > 0")
    expectation(for: webViewHasContent, evaluatedWith: webView, handler: nil)
    waitForExpectations(timeout: 10, handler: nil)

    XCTAssertGreaterThan(webView.staticTexts.count, 0, "Web view should contain text content for test case: \(testCaseName)")
  }
}
