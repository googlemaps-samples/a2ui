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

struct GoogleMapsPinIcon: View {
  var size: CGFloat = 14

  private static let pinImage: UIImage? = {
    let b64 =
      "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"
    if let data = Data(base64Encoded: b64) {
      return UIImage(data: data)
    }
    return nil
  }()

  var body: some View {
    if let image = Self.pinImage {
      Image(uiImage: image)
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: size, height: size)
    } else {
      Image(systemName: "mappin.circle.fill")
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: size, height: size)
    }
  }
}

struct GroundingSourcesView: View {
  let sources: [GroundingSource]
  @State private var isExpanded: Bool = false

  private var columns: [GridItem] {
    if sources.count == 1 {
      return [GridItem(.flexible(maximum: 240))]
    }
    return [
      GridItem(.flexible(), spacing: 8),
      GridItem(.flexible(), spacing: 8),
    ]
  }

  var body: some View {
    if sources.isEmpty {
      EmptyView()
    } else {
      VStack(alignment: .leading, spacing: 8) {
        // Pill Button
        Button(action: {
          withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            isExpanded.toggle()
          }
        }) {
          HStack(spacing: 6) {
            Text("Sources")
              .font(.caption)
              .fontWeight(.semibold)
              .foregroundColor(.primary)

            HStack(spacing: 3) {
              GoogleMapsPinIcon(size: 14)

              Text("\(sources.count)")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.accentColor)
            }

            Image(systemName: "chevron.down")
              .font(.system(size: 10, weight: .semibold))
              .foregroundColor(.secondary)
              .rotationEffect(.degrees(isExpanded ? 180 : 0))
          }
          .padding(.horizontal, 10)
          .padding(.vertical, 5)
          .background(Color(UIColor.systemGray5))
          .clipShape(Capsule())
          .overlay(
            Capsule()
              .stroke(Color(UIColor.separator).opacity(0.6), lineWidth: 1)
          )
        }
        .buttonStyle(PlainButtonStyle())

        // Collapsible Drawer
        if isExpanded {
          LazyVGrid(columns: columns, spacing: 8) {
            ForEach(sources) { source in
              GroundingSourceCard(source: source)
            }
          }
          .transition(.opacity.combined(with: .move(edge: .top)))
        }
      }
      .padding(.vertical, 4)
    }
  }
}

struct GroundingSourceCard: View {
  let source: GroundingSource

  var body: some View {
    Group {
      if let url = URL(string: source.url) {
        Link(destination: url) {
          cardContent
        }
      } else {
        cardContent
      }
    }
    .buttonStyle(PlainButtonStyle())
  }

  private var cardContent: some View {
    VStack(alignment: .leading, spacing: 5) {
      HStack(spacing: 4) {
        GoogleMapsPinIcon(size: 13)

        Text("Google Maps")
          .font(.caption2)
          .fontWeight(.medium)
          .foregroundColor(.secondary)
      }

      Text(source.title)
        .font(.caption)
        .fontWeight(.semibold)
        .foregroundColor(.primary)
        .lineLimit(3)
        .multilineTextAlignment(.leading)
        .fixedSize(horizontal: false, vertical: true)

      Spacer(minLength: 0)
    }
    .padding(.horizontal, 10)
    .padding(.vertical, 8)
    .frame(maxWidth: .infinity, minHeight: 62, alignment: .topLeading)
    .background(Color(UIColor.systemGray5))
    .clipShape(RoundedRectangle(cornerRadius: 14))
    .overlay(
      RoundedRectangle(cornerRadius: 14)
        .stroke(Color(UIColor.separator).opacity(0.4), lineWidth: 1)
    )
  }
}
