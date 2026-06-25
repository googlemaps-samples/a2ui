# Maps Agentic UI Toolkit iOS Demo App

This is an example iOS application demonstrating the Maps Agentic UI Toolkit. It leverages the [`GoogleMapsA2UI`](https://github.com/googlemaps/a2ui/tree/main/client/ios) module to parse backend A2A payloads and render A2UI messages natively.

## Library Dependency

This application relies on the core `GoogleMapsA2UI` module. To set up this dependency for the sample app, add it as a local Swift Package:

1. Open the sample project in Xcode.
2. Follow the [How to Integrate] steps from the [`GoogleMapsA2UI` README](https://github.com/googlemaps/a2ui/tree/main/client/ios/README.md). 
   * **Note:** When prompted for the package location, use the local path to the `a2ui/client/ios/GoogleMapsA2UI` folder.

## Project Structure

*   `ChatApp.swift`: The main application entry point.
*   `ChatView.swift`: The main chat UI, displaying message history, the input bar, and toggles to switch between data grounding modes (e.g., Vertex AI Maps Grounding vs. MCP Lite).
*   `ChatViewModel.swift`: Handles all networking with the backend protocols, maintains state, and routes A2A responses to the `GoogleMapsA2UI` library parser.
*   `Models.swift`: Basic data structures for chat messages and grounding mode configurations.
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
*   **Select Grounding Mode:** Use the radio buttons above the chat bar to toggle between **Grounding Lite (MCP)** and **Grounding with Google Maps (Vertex)**.
*   **Use Canned Prompts:** Tap the **Flask** or **List** icons next to the text input for a menu of pre-written test scenarios.
*   **Send Custom Prompts:** Type a query into the text box (e.g., *"Show me 3 Chinese restaurants in Seattle"*) and hit send.
*   **Interact with Maps:** Wait for the A2UI components to load. You can interact with the rendered maps and place cards (like tapping `Get Directions`) to trigger native Swift callbacks.

## Running UI Tests

We provide an automated script to spin up a local backend server and execute the Xcode UI tests using the iOS Simulator. 

To run the full test suite from the terminal:
```bash
cd /client/ios
./run_xcode_ui_tests.sh
```
> **Note:** The test script automatically starts the backend server by looking for `run_aikit_demo.sh` at `../../../a2ui`. If you cloned the `a2ui` repository to a different location, you must open `run_xcode_ui_tests.sh` and update that path before running the tests.

**Note:** This script requires the `iPhone 17 Pro` simulator to be installed on your system. It automatically launches the backend server and safely shuts it down once the UI tests finish. Additionally, ensure the `GoogleMapsA2UI` Swift Package has been successfully compiled in Xcode before running this script.

## Customizing the Web Components

The `A2UIView` component from the `GoogleMapsA2UI` library is a native wrapper around a `WKWebView`. It does not draw the actual map cards using Swift. Instead, it loads a local `index.html` file that is built from the React web application using lit renderer located in `client/web/react`.

**What can you customize?**
By modifying the React web application located at `client/web/react` (specifically `src/AppMobile.tsx`), you can alter the A2UIView's visual specifications. For example:
*   **Styling & Layout:** Change background colors, sizes, fonts, or padding of the rendering surface.
*   **Component Behavior:** Inject CSS transforms or resizing logic (e.g., our existing hack that forces `<gmp-place-details-compact>` to render image thumbnails even inside narrow iOS chat bubbles).
*   **Native Bridge Integration:** Add or modify JavaScript callbacks that communicate with the native Swift layer.

**Why do you need to rebuild `index.html`?**
Because the iOS `GoogleMapsA2UI` library relies entirely on the local `index.html` bundle to define its visual rendering spec, any changes you make in the React codebase must be re-compiled into a new, minified bundle and copied into the iOS project.

To update the iOS app with your web customizations:

1. Navigate to the web project and run the mobile build:
   ```bash
   cd client/web/react
   npm install
   # This creates the mobile-specific index.html bundle
   npm run build:mobile 
   ```

2. Copy the `dist/index.html` file to the `client/ios/GoogleMapsA2UI/Sources/GoogleMapsA2UI/Resources/index.html` file in your cloned `a2ui` core repository.
