//
// Copyright 2026 Google LLC.
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

package com.example.maui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONException
import org.json.JSONObject

/** Reusable assertions for testing the A2UI [WebView] surface on Android. */
internal object A2UIWebViewAssertions {

  /** A2UI custom element tags; the assertions look these up by tag name. */
  object Components {
    /** A2UI map; see `//third_party/googlemaps/a2ui/client/web/src/lit/custom-components`. */
    const val MAP = "a2ui-googlemap"

    /** The A2UI place details card component. */
    const val PLACE_DETAILS = "a2ui-placedetailscompact"
  }

  /** Waits for an A2UI surface to attach and lay out; says nothing about what the page rendered. */
  fun awaitSurface(rule: MainActivityRule) {
    rule.waitUntil(SURFACE_TIMEOUT_MS) { surfaceNodes(rule).isNotEmpty() }
    rule.waitUntil(RENDER_TIMEOUT_MS) { surfaceHeightPx(rule) > 0 }
  }

  /** Asserts the surface height matches the height the page reported; invalid after a reflow. */
  fun assertDynamicHeightMatchesContent(rule: MainActivityRule) {
    val density = rule.activity.resources.displayMetrics.density
    var inventory = surfaceInventory(rule)
    var heightPx = surfaceHeightPx(rule)
    val deadline = SystemClock.uptimeMillis() + HEIGHT_TIMEOUT_MS
    // The bridge is debounced inside a ResizeObserver, so comparing once would race it.
    while (!heightMatchesContent(heightPx, inventory.contentHeightCssPx, density)) {
      if (SystemClock.uptimeMillis() >= deadline) break
      SystemClock.sleep(POLL_INTERVAL_MS)
      inventory = surfaceInventory(rule)
      heightPx = surfaceHeightPx(rule)
    }

    // The loop can exit holding a zero, and 0 vs a page-reported 0 satisfies the tolerance below.
    assertWithMessage(
        "The A2UI surface collapsed to 0px tall, so WebAppInterface.onWebpageResized() never " +
          "delivered a content height. ${inventory.describe()}"
      )
      .that(heightPx)
      .isGreaterThan(0)

    val expectedHeight = expectedHeightPx(inventory.contentHeightCssPx, density)
    assertWithMessage(
        "The A2UI surface is ${heightPx}px tall but the page reported " +
          "${inventory.contentHeightCssPx} CSS px, which at density $density should have sized " +
          "it to ${expectedHeight}px. The resize bridge delivered a stale height and never " +
          "caught up within ${HEIGHT_TIMEOUT_MS}ms. ${inventory.describe()}"
      )
      .that(abs(heightPx - expectedHeight))
      .isAtMost(HEIGHT_TOLERANCE_PX)
  }

  /** Asserts [tag] has a non-zero box and that Maps upgraded the element inside it. */
  fun assertComponentRendered(rule: MainActivityRule, tag: String) {
    val inventory = awaitInventory(rule) { it.components[tag]?.isRendered == true }
    val component = inventory.components[tag]

    assertWithMessage(
        "No <$tag> rendered in the A2UI surface within ${CONTENT_TIMEOUT_MS}ms. " +
          inventory.describe()
      )
      .that(component)
      .isNotNull()

    assertWithMessage(
        "<$tag> is in the DOM but measures $component, which the user sees as an empty box. If " +
          "it is sized but not upgraded, the Maps JS bundle never loaded: check for " +
          "ERR_NAME_NOT_RESOLVED on maps.googleapis.com in logcat, and confirm the target still " +
          "carries the \"requires-net:external\" tag. ${inventory.describe()}"
      )
      .that(component?.isRendered)
      .isTrue()
  }

  // ---------------------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------------------

  /** Polls until [condition] holds, then returns the last reading so callers can quote it. */
  private fun awaitInventory(
    rule: MainActivityRule,
    condition: (SurfaceInventory) -> Boolean,
  ): SurfaceInventory {
    var inventory = surfaceInventory(rule)
    val deadline = SystemClock.uptimeMillis() + CONTENT_TIMEOUT_MS
    while (!condition(inventory) && SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(POLL_INTERVAL_MS)
      inventory = surfaceInventory(rule)
    }
    // Fail differently here: an empty walk cannot tell an unrendered page from a stale probe.
    assertWithMessage(
        "The DOM probe found no A2UI component in the surface. Either the page never rendered, " +
          "or the probe can no longer walk into the components' shadow roots. " +
          inventory.describe()
      )
      .that(inventory.components)
      .isNotEmpty()
    return inventory
  }

  private fun heightMatchesContent(
    heightPx: Int,
    contentHeightCssPx: Int,
    density: Float,
  ): Boolean =
    heightPx > 0 &&
      abs(heightPx - expectedHeightPx(contentHeightCssPx, density)) <= HEIGHT_TOLERANCE_PX

  /** Mirrors the conversion in `WebAppInterface.onWebpageResized`. */
  private fun expectedHeightPx(contentHeightCssPx: Int, density: Float): Int =
    (contentHeightCssPx * density).toInt()

  private fun surfaceNodes(rule: MainActivityRule) =
    rule.onAllNodesWithTag(SURFACE_TAG).fetchSemanticsNodes()

  /** Height of the newest surface, in device pixels; the AndroidView wraps the WebView exactly. */
  private fun surfaceHeightPx(rule: MainActivityRule): Int =
    surfaceNodes(rule).lastOrNull()?.size?.height ?: 0

  /** Reads the surface once. */
  private fun surfaceInventory(rule: MainActivityRule): SurfaceInventory =
    SurfaceInventory.parse(evaluateJavaScript(rule, SURFACE_INVENTORY_JS))

  /** Runs [script] in the newest surface's WebView and returns its JSON-encoded result. */
  private fun evaluateJavaScript(rule: MainActivityRule, script: String): String {
    val latch = CountDownLatch(1)
    val result = AtomicReference("")
    val deadline = SystemClock.uptimeMillis() + WEBVIEW_TIMEOUT_MS
    var dispatched = false
    // Waits for a WebView rather than demanding one: the LazyColumn can recycle the surface.
    while (!dispatched) {
      // Find and call in one hop, so the WebView cannot detach between the lookup and the call.
      dispatched = rule.runOnUiThread {
        val webView = findLastWebView(rule.activity.window.decorView)
        webView?.evaluateJavascript(script) { value ->
          result.set(value)
          latch.countDown()
        }
        webView != null
      }
      if (dispatched || SystemClock.uptimeMillis() >= deadline) break
      SystemClock.sleep(POLL_INTERVAL_MS)
    }

    assertWithMessage(
        "No WebView attached within ${WEBVIEW_TIMEOUT_MS}ms; A2UIView should have been composed " +
          "for the reply."
      )
      .that(dispatched)
      .isTrue()
    assertWithMessage("evaluateJavascript did not call back within ${JS_TIMEOUT_MS}ms.")
      .that(latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS))
      .isTrue()
    return result.get()
  }

  /** Returns the most recently attached [WebView], i.e. the surface for the newest reply. */
  private fun findLastWebView(view: View): WebView? {
    // WebView extends AbsoluteLayout, so this check must precede the ViewGroup one.
    if (view is WebView) return view
    if (view is ViewGroup) {
      for (i in view.childCount - 1 downTo 0) {
        findLastWebView(view.getChildAt(i))?.let {
          return it
        }
      }
    }
    return null
  }

  /** An A2UI component's box, in CSS pixels, and whether Maps owns the element inside it. */
  data class ComponentState(val width: Double, val height: Double, val upgraded: Boolean) {
    val isRendered: Boolean
      get() = width > 0 && height > 0 && upgraded

    override fun toString(): String = "${width}x$height upgraded=$upgraded"
  }

  /** What the A2UI surface currently renders, as seen from inside the page. */
  data class SurfaceInventory(
    /** Elements the walk counted: `document` plus every shadow root it could enter. */
    val elementCount: Int,
    /** The A2UI components found by the walk, keyed by tag name, absent ones omitted. */
    val components: Map<String, ComponentState>,
    /** Height `a2ui-shell` reports over the resize bridge, in CSS pixels. */
    val contentHeightCssPx: Int,
  ) {
    /** Renders the whole reading into a failure message, so one failure is enough to diagnose. */
    fun describe(): String =
      "Surface: elements=$elementCount, contentHeight=${contentHeightCssPx}css-px, " +
        "components=$components"

    companion object {
      fun parse(json: String): SurfaceInventory {
        val root =
          try {
            JSONObject(json)
          } catch (e: JSONException) {
            throw AssertionError(
              "The DOM probe returned $json instead of an inventory; the script threw inside the " +
                "WebView.",
              e,
            )
          }
        val components = root.getJSONObject("components")
        return SurfaceInventory(
          elementCount = root.getInt("elementCount"),
          components =
            components.keys().asSequence().associateWith {
              toComponentState(components.getJSONObject(it))
            },
          contentHeightCssPx = root.getInt("contentHeightCssPx"),
        )
      }

      private fun toComponentState(json: JSONObject) =
        ComponentState(
          json.getDouble("width"),
          json.getDouble("height"),
          json.getBoolean("upgraded"),
        )
    }
  }

  /** `testTag` of the `AndroidView` wrapping A2UIView; see `MainActivity.kt`. */
  private const val SURFACE_TAG = "gmpA2UIView"

  /** Budget for the canned response to arrive and the surface to be composed. */
  private const val SURFACE_TIMEOUT_MS = 15_000L

  /** Budget for the surface to lay out once it exists; excludes response latency. */
  private const val RENDER_TIMEOUT_MS = 15_000L

  /** Budget for the page to render its components; ~7x the roughly 2s measured. */
  private const val CONTENT_TIMEOUT_MS = 15_000L

  /** Budget for the debounced resize bridge to settle on the content height. */
  private const val HEIGHT_TIMEOUT_MS = 10_000L

  /** Budget for the surface item to be recomposed, e.g. after the LazyColumn recycles it. */
  private const val WEBVIEW_TIMEOUT_MS = 10_000L

  private const val JS_TIMEOUT_MS = 10_000L

  private const val POLL_INTERVAL_MS = 100L

  /** Slack for a comparison that should be exact; the residual pixel has never been measured. */
  private const val HEIGHT_TOLERANCE_PX = 2

  /** Maps-owned elements, which only exist once the Maps JS bundle has registered them. */
  private const val MAP_3D_ELEMENT = "gmp-map-3d"

  private const val PLACE_DETAILS_ELEMENT = "gmp-place-details-compact"

  /** Host page root, and the element the resize bridge measures inside it. */
  private const val SHELL = "a2ui-shell"

  private const val CONTENT_SELECTOR = ".chat-messages"

  /** Reads the surface in one round trip, entering closed shadow roots via Lit's `renderRoot`. */
  private val SURFACE_INVENTORY_JS =
    """
    (function () {
      var out = {elementCount: 0, components: {}, contentHeightCssPx: 0};
      var visited = [];

      // The Maps-owned element each A2UI component is expected to be hosting.
      var CHILD_OF = {
        '${Components.MAP}': '$MAP_3D_ELEMENT',
        '${Components.PLACE_DETAILS}': '$PLACE_DETAILS_ELEMENT'
      };

      function rootOf(el) {
        var root = el.shadowRoot || el.renderRoot;
        // createRenderRoot() may return the host itself, which would make the walk loop forever.
        return root && root !== el && root.querySelectorAll ? root : null;
      }

      // A live `tagName`: upgraded by Maps (CSS cannot fake `instanceof`) and not collapsed.
      function upgraded(host, tagName) {
        var ctor = customElements.get(tagName);
        if (!ctor) return false;
        var root = rootOf(host);
        if (!root) return false;
        var el = root.querySelector(tagName);
        if (!el || !(el instanceof ctor)) return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      }

      function walk(root) {
        if (visited.indexOf(root) !== -1) return;
        visited.push(root);
        var all = root.querySelectorAll('*');
        out.elementCount += all.length;
        for (var i = 0; i < all.length; i++) {
          var el = all[i];
          var tag = el.tagName.toLowerCase();
          // First wins: the outermost card is the one the user sees.
          if (CHILD_OF.hasOwnProperty(tag) && !out.components.hasOwnProperty(tag)) {
            var rect = el.getBoundingClientRect();
            out.components[tag] = {
              width: rect.width,
              height: rect.height,
              upgraded: upgraded(el, CHILD_OF[tag])
            };
          }
          var childRoot = rootOf(el);
          if (childRoot) walk(childRoot);
        }
      }

      walk(document);

      // The height setupResizer() feeds to WebAppInterface.onWebpageResized().
      var shell = document.querySelector('$SHELL');
      var shellRoot = shell ? rootOf(shell) : null;
      var content = shellRoot ? shellRoot.querySelector('$CONTENT_SELECTOR') : null;
      if (content) out.contentHeightCssPx = content.scrollHeight;

      return out;
    })();
    """
      .trimIndent()
}

/** The rule type that `createAndroidComposeRule<MainActivity>()` produces. */
internal typealias MainActivityRule =
  AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
