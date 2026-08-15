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

import SwiftUI

struct TestCasesMenuView: View {
  @Binding var inputText: String
  @AppStorage(MockScenarioRegistry.useCannedResponsesKey) private var useCannedResponses = false

  var body: some View {
    HStack {
      Menu {
        #if DEBUG
          Toggle("Use Canned Responses", isOn: $useCannedResponses)
          Divider()
        #endif

        Section("TestCases") {
          ForEach(MockScenarioRegistry.scenarios) { scenario in
            Button(scenario.buttonName) {
              inputText = scenario.query
            }
          }
        }
      } label: {
        Image(systemName: "flask.fill")
          .font(.title2)
          .foregroundColor(.orange)
      }

      Menu {
        Section("Restaurant Finder") {
          Button("Seattle Indian") {
            inputText = "Show 3 Indian Restaurants in Seattle"
          }
          Button("NYC Chinese") {
            inputText = "Show top Chinese restaurants in New York"
          }
          Button("San Jose Ethiopian") {
            inputText = "Show 2 Ethiopian Restaurants in San Jose, CA"
          }
          Button("Seattle Sushi") {
            inputText = "Show me some good sushi in Seattle"
          }
        }
        Section("Place Details") {
          Button("Parking at Milstead") {
            inputText = "Is there parking near Milstead Coffee?"
          }
          Button("Vegan at Pablo y Pablo") {
            inputText = "Are there vegan options at Pablo y Pablo?"
          }
        }
        Section("Routes") {
          Button("Seattle to LA") {
            inputText =
              "I am going from Seattle to LA by car. I want to make stops to get food and rest. Can you show me route options, including stop points? I am also sensitive to air quality, if you could tell me the forecast along the route, thanks!"
          }
          Button("Vegetarian House to Din Tai Fung") {
            inputText = "How to get from Vegetarian House to Din Tai Fung in San Jose CA"
          }
          Button("Directions to Hadilao") {
            inputText = "Get me directions to Hadilao Hot Pot Cupertino"
          }
        }
        Section("Location Analysis") {
          Button("New Home Location") {
            inputText =
              "I'm considering buying a new home at <2200 N 56th St, Seattle, WA 98103> Do you think this a good location to get to my work at Google Fremont in Seattle? Can I easily get my morning latte at Milstead on my way to work? Are there any public tennis courts nearby? Most importantly, am I close enough to a Din Tai Fung for sunday dinner?"
          }
        }
      } label: {
        Image(systemName: "list.bullet.circle.fill")
          .font(.title2)
          .foregroundColor(.blue)
      }
    }
  }
}
