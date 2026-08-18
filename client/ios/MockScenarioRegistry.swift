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

/// Defines a unified mock scenario encompassing the UI button, text query, and expected JSON file.
struct MockScenario: Identifiable {
  let id = UUID()
  let buttonName: String
  let query: String
  let fileName: String
}

/// A registry mapping predefined test case strings to their mock JSON filenames.
enum MockScenarioRegistry {
  /// The UserDefaults key used to store the developer preference for mocking.
  static let useCannedResponsesKey = "UI_TEST_USE_CANNED_RESPONSES"

  static let scenarios: [MockScenario] = [
    MockScenario(
      buttonName: "Seattle Coffee Shops",
      query: "Show me 5 coffee shops near South Lake Union in Seattle",
      fileName: "SeattleCoffeeShops"),
    MockScenario(
      buttonName: "MV Google Gyms",
      query: "Show me Google Office Buildings in the Mountain View area which have a gym",
      fileName: "MVGoogleGyms"),
    MockScenario(
      buttonName: "Edgewater Hotel", query: "Is the Edgewater Hotel in Seattle a good hotel?",
      fileName: "EdgewaterHotel"),
    MockScenario(
      buttonName: "Gas Works Park",
      query:
        "How do I get to Gas Works Park in Fremont from my location (the Edgewater Hotel in Seattle)?",
      fileName: "GasWorksPark"),
    MockScenario(
      buttonName: "Kirkland Commute",
      query:
        "How long will it take to commute to Google Kirkland office from downtown Redmond during my morning rush hour commute?",
      fileName: "KirklandCommute"),
    MockScenario(
      buttonName: "Le Petite Academy",
      query:
        "How long will it take to go to Le Petite Academy of Kirkland and the Google Kirkland office starting from downtown Redmond during my morning rush hour commute?",
      fileName: "LePetiteAcademy"),
    MockScenario(
      buttonName: "NYC Attractions",
      query:
        "How far away are the top 5 major NYC tourist attractions from the Waldorf Astoria New York hotel? Show me all the routes to each of these locations from the Waldorf Astoria Hotel in New York.",
      fileName: "NYCAttractions"),
    MockScenario(
      buttonName: "SLU Salads (Vegan)",
      query:
        "Show me 5 lunch restaurants with Salads in South Lake Union. Which ones of these have vegan friendly options?",
      fileName: "SLUSaladsVegan"),
    MockScenario(
      buttonName: "SLU Salads (Click)",
      query:
        "Show me 5 lunch restaurants with Salads in South Lake Union. (Inject 'click' to get directions on the 2nd option)",
      fileName: "SLUSaladsClick"),
    MockScenario(
      buttonName: "SLU Salads (Directions)",
      query:
        "Show me 5 lunch restaurants with Salads in South Lake Union. Give me directions to the 2nd one (starting from the Google South Lake Union WLK building)",
      fileName: "SLUSaladsDirections"),
    MockScenario(
      buttonName: "London Itinerary",
      query: "Give me a 3 day itinerary for a family of 3 traveling to London",
      fileName: "LondonItinerary"),
  ]
}
