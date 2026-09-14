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

package com.example.maui.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maui.GroundingSource
import com.example.maui.R

@Composable
fun GroundingSourcesView(sources: List<GroundingSource>, modifier: Modifier = Modifier) {
  if (sources.isEmpty()) return

  var isExpanded by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    // Pill Button
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
      modifier = Modifier.clickable { isExpanded = !isExpanded },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Sources",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_google_maps_pin),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = sources.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A73E8),
          )
        }

        Icon(
          painter = painterResource(id = R.drawable.ic_chevron_down),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp).rotate(if (isExpanded) 180f else 0f),
        )
      }
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        sources.chunked(2).forEach { rowSources ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rowSources.forEach { source ->
              Box(modifier = Modifier.weight(1f)) { GroundingSourceCard(source) }
            }
            if (rowSources.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
fun GroundingSourceCard(source: GroundingSource, modifier: Modifier = Modifier) {
  val context = LocalContext.current

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    modifier =
      modifier.fillMaxWidth().clickable {
        try {
          val rawUrl = source.url
          val targetUri =
            if (rawUrl.contains("/maps/place/?q=place_id:")) {
              val placeId = rawUrl.substringAfter("/maps/place/?q=place_id:")
              val queryParam = Uri.encode(source.title.replace(" · ", ", "))
              Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=$queryParam&query_place_id=$placeId"
              )
            } else {
              Uri.parse(rawUrl)
            }
          val intent =
            Intent(Intent.ACTION_VIEW, targetUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
          context.startActivity(intent)
        } catch (e: Exception) {
          android.util.Log.e("GroundingSourcesView", "Error opening map url: ${source.url}", e)
        }
      },
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_google_maps_pin),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Google Maps",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
      Text(
        text = source.title,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
