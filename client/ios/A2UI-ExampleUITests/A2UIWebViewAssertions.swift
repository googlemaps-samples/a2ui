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
import XCTest

/// Reusable assertions for testing A2UI WKWebViews on iOS.
enum A2UIWebViewAssertions {

  /// Accessibility label constants matching web component UIStrings.
  enum Labels {
    static let mapView = "Map View"
    static let placeDetails = "Place Details"
    static let openInMaps = "Open in Maps (opens in new tab)"
  }

  /// Waits for the WKWebView to attach and become visible.
  @discardableResult
  static func waitForWebView(
    in app: XCUIApplication,
    timeout: TimeInterval = 15.0,
    file: StaticString = #file,
    line: UInt = #line
  ) -> XCUIElement {
    let webView = app.webViews.firstMatch
    let exists = webView.waitForExistence(timeout: timeout)
    XCTAssertTrue(
      exists,
      "WKWebView should be attached and present within \(timeout)s.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      webView.frame.width, 0,
      "WKWebView width should be > 0.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      webView.frame.height, 0,
      "WKWebView height should be > 0.",
      file: file,
      line: line
    )
    return webView
  }

  /// Default WKWebView height in A2UIView.swift, before heightObserver fires.
  private static let placeholderHeight: CGFloat = 100

  /// Asserts the heightObserver bridge resized the WKWebView off its default.
  static func assertDynamicHeightExpanded(
    for webView: XCUIElement,
    timeout: TimeInterval = 5.0,
    file: StaticString = #file,
    line: UInt = #line
  ) {
    let predicate = NSPredicate { _, _ in
      let height = webView.frame.height
      return height > 0 && height != placeholderHeight
    }
    let expectation = XCTNSPredicateExpectation(
      predicate: predicate, object: nil
    )
    let result = XCTWaiter.wait(for: [expectation], timeout: timeout)

    XCTAssertGreaterThan(
      webView.frame.height,
      0,
      "WKWebView collapsed to 0pt height.",
      file: file,
      line: line
    )
    XCTAssertEqual(
      result,
      .completed,
      "WKWebView height is still the \(placeholderHeight)pt placeholder after "
        + "\(timeout)s; the heightObserver bridge never delivered a content "
        + "height.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      webView.frame.width,
      0,
      "WKWebView width (\(webView.frame.width)pt) must be > 0.",
      file: file,
      line: line
    )
  }

  /// Asserts that the app window matches the requested orientation.
  static func assertOrientation(
    for app: XCUIApplication,
    isLandscape: Bool,
    timeout: TimeInterval = 5.0,
    file: StaticString = #file,
    line: UInt = #line
  ) {
    let predicate = NSPredicate { _, _ in
      if isLandscape {
        return app.frame.width > app.frame.height
      } else {
        return app.frame.height > app.frame.width
      }
    }
    let expectation = XCTNSPredicateExpectation(
      predicate: predicate, object: nil
    )
    let result = XCTWaiter.wait(for: [expectation], timeout: timeout)

    let message =
      isLandscape
      ? "Screen width (\(app.frame.width)pt) must be > height "
        + "(\(app.frame.height)pt) in Landscape."
      : "Screen height (\(app.frame.height)pt) must be > width "
        + "(\(app.frame.width)pt) in Portrait."

    XCTAssertEqual(result, .completed, message, file: file, line: line)
  }

  /// Asserts that a place thumbnail renders with positive dimensions.
  static func assertThumbnailRendered(
    in webView: XCUIElement,
    timeout: TimeInterval = 10.0,
    file: StaticString = #file,
    line: UInt = #line
  ) {
    let image = webView.images.firstMatch
    XCTAssertTrue(
      image.waitForExistence(timeout: timeout),
      "Thumbnail image must exist in WebView within \(timeout)s.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      image.frame.width, 0,
      "Thumbnail image width (\(image.frame.width)pt) must be > 0.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      image.frame.height, 0,
      "Thumbnail image height (\(image.frame.height)pt) must be > 0.",
      file: file,
      line: line
    )
  }

  /// Asserts that a labelled element renders with positive dimensions.
  static func assertElement(
    withLabel label: String,
    renderedIn webView: XCUIElement,
    timeout: TimeInterval = 10.0,
    file: StaticString = #file,
    line: UInt = #line
  ) {
    let predicate = NSPredicate(
      format: "label == %@ OR identifier == %@ OR label CONTAINS[c] %@",
      label, label, label
    )
    let element = webView.descendants(matching: .any)
      .matching(predicate)
      .firstMatch

    XCTAssertTrue(
      element.waitForExistence(timeout: timeout),
      "Element with label '\(label)' must exist in WebView within \(timeout)s.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      element.frame.width, 0,
      "Element '\(label)' width (\(element.frame.width)pt) must be > 0.",
      file: file,
      line: line
    )
    XCTAssertGreaterThan(
      element.frame.height, 0,
      "Element '\(label)' height (\(element.frame.height)pt) must be > 0.",
      file: file,
      line: line
    )
  }

  /// Asserts the "Open in Maps" link exists; it has no hittable box to tap.
  static func assertOpenInMapsLinkRendered(
    in webView: XCUIElement,
    timeout: TimeInterval = 10.0,
    file: StaticString = #file,
    line: UInt = #line
  ) {
    let link = webView.links[Labels.openInMaps]
    XCTAssertTrue(
      link.waitForExistence(timeout: timeout),
      "\"\(Labels.openInMaps)\" link must exist in WebView within "
        + "\(timeout)s.",
      file: file,
      line: line
    )
  }
}
