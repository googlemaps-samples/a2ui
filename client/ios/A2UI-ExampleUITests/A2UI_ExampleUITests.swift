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

/// UI test suite for the A2UI iOS sample application.
final class A2UIExampleUITests: XCTestCase {

  private let app = XCUIApplication()

  override func setUpWithError() throws {
    continueAfterFailure = false
  }

  override func tearDownWithError() throws {
    // Reset orientation to portrait after every test to avoid cascading
    // state pollution.
    if XCUIDevice.shared.orientation != .portrait {
      XCUIDevice.shared.orientation = .portrait
    }
  }

  // =========================================================================
  // Core Architectural UI Tests
  // =========================================================================

  /// Verify WebView height dynamically expands to fit web content.
  func testDynamicHeight_expandsToFitContent() throws {
    let webView = try runTestCase(named: "Seattle Coffee Shops")
    A2UIWebViewAssertions.assertDynamicHeightExpanded(for: webView)
  }

  /// Verify place thumbnail photo is visible and not collapsed.
  func testPlaceThumbnail_rendersWithValidDimensions() throws {
    let webView = try runTestCase(named: "Seattle Coffee Shops")
    A2UIWebViewAssertions.assertThumbnailRendered(in: webView)
  }

  /// Verify the "Open in Maps" link is rendered in the place card.
  func testOpenInMapsLink_renders() throws {
    let webView = try runTestCase(named: "Seattle Coffee Shops")
    A2UIWebViewAssertions.assertOpenInMapsLinkRendered(in: webView)
  }

  /// Verify rotating screen updates layout, recalculates height, and preserves
  /// components.
  func testDeviceRotation_updatesLayoutAndPreservesCards() throws {
    let webView = try runTestCase(named: "Edgewater Hotel")

    // 1. Verify initial Portrait layout
    A2UIWebViewAssertions.assertOrientation(for: app, isLandscape: false)
    A2UIWebViewAssertions.assertDynamicHeightExpanded(for: webView)

    // 2. Rotate to Landscape and verify dimensions, component preservation,
    // and height.
    XCUIDevice.shared.orientation = .landscapeLeft
    A2UIWebViewAssertions.assertOrientation(for: app, isLandscape: true)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.mapView, renderedIn: webView)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.placeDetails, renderedIn: webView)
    A2UIWebViewAssertions.assertDynamicHeightExpanded(for: webView)

    // 3. Rotate back to Portrait and verify restored layout, component
    // preservation, and height.
    XCUIDevice.shared.orientation = .portrait
    A2UIWebViewAssertions.assertOrientation(for: app, isLandscape: false)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.mapView, renderedIn: webView)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.placeDetails, renderedIn: webView)
    A2UIWebViewAssertions.assertDynamicHeightExpanded(for: webView)
  }

  // =========================================================================
  // Canned Response Tests
  // =========================================================================

  /// Verifies Seattle Coffee Shops renders Map View and Place Details.
  func testSeattleCoffeeShops_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "Seattle Coffee Shops")
  }

  /// Verifies MV Google Gyms renders Map View and Place Details.
  func testMVGoogleGyms_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "MV Google Gyms")
  }

  /// Verifies Edgewater Hotel renders Map View and Place Details.
  func testEdgewaterHotel_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "Edgewater Hotel")
  }

  /// Verifies Gas Works Park renders Map View and Place Details.
  func testGasWorksPark_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "Gas Works Park")
  }

  /// Verifies Kirkland Commute renders Map View and Place Details.
  func testKirklandCommute_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "Kirkland Commute")
  }

  /// Verifies Le Petite Academy renders Map View and Place Details.
  func testLePetiteAcademy_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "Le Petite Academy")
  }

  /// Verifies NYC Attractions renders Map View and Place Details.
  func testNYCAttractions_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "NYC Attractions")
  }

  /// Verifies SLU Salads (Vegan) renders Map View and Place Details.
  func testSLUSaladsVegan_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "SLU Salads (Vegan)")
  }

  /// Verifies SLU Salads (Click) renders Map View and Place Details.
  func testSLUSaladsClick_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "SLU Salads (Click)")
  }

  /// Verifies SLU Salads (Directions) renders Map View and Place Details.
  func testSLUSaladsDirections_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "SLU Salads (Directions)")
  }

  /// Verifies London Itinerary renders Map View and Place Details.
  func testLondonItinerary_rendersMapAndPlaceDetails() throws {
    try runAndVerifyCannedTestCase(named: "London Itinerary")
  }

  // MARK: - Helper Methods

  /// Sends the scenario prompt and returns the loaded web view.
  @discardableResult
  private func runTestCase(named testCaseName: String) throws -> XCUIElement {
    let mockFileName = try XCTUnwrap(
      MockScenarioRegistry.scenarios.first(where: {
        $0.buttonName == testCaseName
      })?.fileName,
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

    // Wait for web surface to load and return
    return A2UIWebViewAssertions.waitForWebView(in: app)
  }

  /// Verifies a canned response renders Map View and Place Details.
  private func runAndVerifyCannedTestCase(named testCaseName: String) throws {
    let webView = try runTestCase(named: testCaseName)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.mapView, renderedIn: webView)
    A2UIWebViewAssertions.assertElement(
      withLabel: A2UIWebViewAssertions.Labels.placeDetails, renderedIn: webView)
  }
}
