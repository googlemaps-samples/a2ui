# Maps Agentic UI Toolkit iOS Demo App

This is an example iOS application demonstrating the Maps Agentic UI Toolkit. It leverages the [`GoogleMapsA2UI`](https://github.com/googlemaps/a2ui/tree/main/client/ios) module to parse backend A2A payloads and render A2UI messages natively.

## Library Dependency

This application relies on the core `GoogleMapsA2UI` module. To set up this dependency for the sample app, add it as a local Swift Package:

1. Open the sample project in Xcode.
2. Follow the [How to Integrate] steps from the [`GoogleMapsA2UI` README](https://github.com/googlemaps/a2ui/tree/main/client/ios/README.md).
   * **Note:** When prompted for the package location, use the local path to the `a2ui/client/ios/GoogleMapsA2UI` folder.

## Project Structure

*   `ChatApp.swift`: The main application entry point.
*   `ChatView.swift`: The main chat UI, displaying message history, the input bar, and toggles to switch between different agent modes (e.g., Vertex AI Maps Grounding vs. MCP Lite vs. Template).
*   `ChatViewModel.swift`: Handles all networking with the backend protocols, maintains state, and routes A2A responses to the `GoogleMapsA2UI` library parser.
*   `Models.swift`: Basic data structures for chat messages and agent mode configurations.
*   `GoogleMapsA2UI`: A Swift package dependency pulled in from the `a2ui` module. It provides the `A2UIView` SwiftUI component to render the dynamic maps components and parses the A2A payload into a list of `ParsedA2AEvent` objects. *(See the [Library Dependency](#library-dependency) section above for integration details).*

## Quickstart Guide

### 1. Set API Keys and Gateway URL

Before running the application, you must configure your API keys and endpoints in `ChatViewModel.swift`.

1. Open `ChatViewModel.swift`.
2. Locate the following variables at the top of the file and replace them with your actual values:
   ```swift
   private let googleMapsApiKey = "YOUR_API_KEY"
   // --- CONFIGURATION ---
   private let activeServer: ServerType = .remote
   private let remoteEndpoint = "REQUIRED_REMOTE_ENDPOINT"
   private let apiKey = "REQUIRED_REMOTE_API_KEY"
   // ---------------------
   ```

* You can create a Google Maps API Key in the [Google Cloud Console](https://mapsplatform.google.com/).

### 2. Connectivity Options

The app is configured to connect to two types of servers by setting the `activeServer` property in `ChatViewModel.swift`:

1.  **`.demo` (Local Demo Server):** The default A2UI server provided in this repository, usually running on `http://localhost:10002`.
2.  **`.remote` (Remote Gateway):** Connects to the cloud-hosted agent gateway using your provided `remoteEndpoint` and `apiKey`.

### 3. Build and Run

Open the project in Xcode (or use your preferred build system) and run the app.

**Connecting to a Local Server (Simulator vs. Physical Device):**
If your `activeServer` is set to `.demo` (This means the server is running on your Mac):
*   **Simulator:** You can leave the URL in `baseUrl` as `http://localhost:10002` (or `127.0.0.1`).
*   **Physical Device:** `localhost` resolves to the iPhone itself, not your Mac. You must find your Mac's Wi-Fi IP address (e.g., run `ipconfig getifaddr en0`). Then, in `ChatViewModel.swift`, update the string returned by `baseUrl` to use this IP (e.g., change `"http://localhost:10002"` to `"http://192.168.68.93:10002"`).
*   **Binding to `0.0.0.0`:** By default, your Mac's server will block connections from outside devices. To allow your physical iPhone to connect, you must start your python server with the `--host 0.0.0.0` flag (e.g., `python server.py --host 0.0.0.0 --port 10002`). This tells the server to listen to the Wi-Fi network instead of just `localhost`.

*(Note: If you are using `.remote`, you do not need to change IPs or host bindings since the gateway is cloud-hosted).*

### 4. Using the Demo

Once the app is running:
*   **Select agent Mode:** Use the radio buttons above the chat bar to toggle between **Grounding Lite (MCP)**, **Grounding with Google Maps (Vertex)** and **Template**.
*   **Use Canned Prompts:** Tap the **Flask** or **List** icons next to the text input for a menu of pre-written test scenarios.
*   **Send Custom Prompts:** Type a query into the text box (e.g., *"Show me 3 Chinese restaurants in Seattle"*) and hit send.
*   **Interact with Maps:** Wait for the A2UI components to load. You can interact with the rendered maps and place cards (like tapping `Get Directions`) to trigger native Swift callbacks.

## Running UI Tests

You can execute the UI tests directly from Xcode:

1. Open the project in Xcode.
2. Open `client/ios/A2UI-ExampleUITests/A2UI_ExampleUITests.swift`.
3. Run the tests by pressing `Cmd + U` or clicking the play button next to the test class (`A2UIExampleUITests`) or individual test methods.

> **Note:** The UI tests rely on a backend server to process the test prompts, and agent responses can sometimes take more than 30 seconds. If your tests are failing due to a timeout, you can increase the timeout value on line 96 of `A2UI_ExampleUITests.swift`:
> ```
> waitForExpectations(timeout: 30, handler: nil) // Increase this value if the server is slow
> ```

## Customizing the Web Components

The `A2UIView` component from the `GoogleMapsA2UI` library is a native wrapper around a `WKWebView`. It does not draw the actual map cards using Swift. Instead, it loads a local `index.html` bundle that compiles the core A2UI web components together with the iOS platform-specific shell logic at `a2ui/client/ios/web_build/`.

**What can you customize?**
By modifying the web components directly within `a2ui/client/ios/web_build/`, you can alter the A2UIView's visual specifications. For example:
*   **Styling & Layout:** Change background colors, sizes, fonts, or padding of the rendering surface.
*   **Component Behavior:** Inject CSS transforms or resizing logic (e.g., our existing hack that forces `<gmp-place-details-compact>` to render image thumbnails even inside narrow iOS chat bubbles).
*   **Native Bridge Integration:** Add or modify JavaScript callbacks that communicate with the native Swift layer.

**Why do you need to rebuild `index.html`?**
Because the iOS `GoogleMapsA2UI` library relies entirely on the local `index.html` bundle to define its visual rendering spec, any changes you make in the web codebase must be re-compiled into a new, minified bundle and copied into the native iOS wrapper project.

To update the iOS app with your web customizations:

1. Navigate to the iOS web build directory:
   ```bash
   cd client/ios/web_build
   ```
2. Install the dependencies and run the build script:
   ```bash
   npm install
   npm run build
   ```
3. This script triggers Vite to compile a newly minified payload and automatically copies it into `GoogleMapsA2UI/Sources/GoogleMapsA2UI/Resources/index.html`.
4. Re-build the iOS sample app from Xcode to view your updated components.

