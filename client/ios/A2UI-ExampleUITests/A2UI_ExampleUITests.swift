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

import XCTest

final class A2UIExampleUITests: XCTestCase {

  let app = XCUIApplication()

  override func setUpWithError() throws {
    continueAfterFailure = false
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
    let mockFileName = try XCTUnwrap(
      MockScenarioRegistry.scenarios.first(where: { $0.buttonName == testCaseName })?.fileName,
      "Error: '\(testCaseName)' is missing from MockScenarioRegistry."
    )

    app.launchEnvironment["UI_TEST_MOCK_SCENARIO"] = mockFileName
    app.launch()

    // Tap the flask icon to open the TestCases menu
    app.buttons["flask.fill"].tap()

    // Tap the specific test case
    app.buttons[testCaseName].firstMatch.tap()

    // Tap the send button (paperplane.fill)
    app.buttons["paperplane.fill"].tap()

    // Wait for the web view to appear, indicating an A2UI response
    let webView = app.webViews.element
    // With hermetic mocking, responses are fast, but WebView initialization may take a few seconds.
    let webViewAppeared = webView.waitForExistence(timeout: 15)

    if !webViewAppeared {
      let allTexts = app.staticTexts.allElementsBoundByIndex.map { $0.label }
      XCTFail(
        "Web view should exist for test case: \(testCaseName). Current texts on screen: \(allTexts)"
      )
    }

    // Ensure the web view contains some text content
    let webViewHasContent = NSPredicate(format: "staticTexts.count > 0")
    expectation(for: webViewHasContent, evaluatedWith: webView, handler: nil)
    waitForExpectations(timeout: 10, handler: nil)

    XCTAssertGreaterThan(
      webView.staticTexts.count, 0,
      "Web view should contain text content for test case: \(testCaseName)")

    // Scroll down so the WebView is fully visible on screen
    app.swipeUp()

  }
}
