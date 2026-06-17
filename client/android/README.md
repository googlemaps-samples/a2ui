# A2UI Android Sample App

## Overview
This directory contains the Android sample application for the Google Maps Agentic UI (A2UI) Toolkit. It demonstrates how to integrate the underlying `GoogleMapsA2UI` Library to render generative AI responses containing interactive map elements and conversational text natively within an Android WebView.

**Compatibility:** This sample app is designed for **A2UI v0.9**. It is not compatible with earlier versions (e.g., v0.8).

## Instructions

### 1. Build and Publish the A2UI SDK Locally

Before building the sample app, you must build the underlying **GoogleMapsA2UI** library (the core A2UI SDK) and publish it to your local Maven repository. 

For instructions on how to build and publish the library, please refer to the [A2UI Android README](https://github.com/googlemaps/a2ui/tree/main/client/android/README.md).

### 2. Set API Keys and Gateway URL

Add your API keys and server connection settings to the `local.properties` file in the root `android` directory (e.g., `ai-kit/a2ui-samples/client/android/local.properties`):

```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk 
MAPS_API_KEY=your_actual_google_maps_api_key_here
GATEWAY_API_KEY=your_actual_gateway_api_key_here
GATEWAY_URL=your_actual_gateway_url_here
```

The build system uses the `secrets-gradle-plugin` to securely inject these values into the app at runtime.

*   **`MAPS_API_KEY`**: Obtain a Google Maps API Key from the Google Cloud Console.
*   **`GATEWAY_URL`** and **`GATEWAY_API_KEY`**: 
    *   **For Remote Server:** If you have deployed a Remote Server to Google Cloud, set `GATEWAY_URL` to your Cloud Run or API Gateway endpoint. Optionally, set `GATEWAY_API_KEY` if your server uses API key-based authentication.
    *   **For Local Server:** Set `GATEWAY_URL` to `http://127.0.0.1:10002` (physical device) or `http://10.0.2.2:10002` (emulator).

*(Note: Before building this sample app, ensure you have built and published the `GoogleMapsA2UI` Android Library locally. See [a2ui/client/android/README.md](https://github.com/googlemaps/a2ui/tree/main/client/android/README.md) for instructions).*

### 3. Server Configuration & Connectivity Options

In `app/src/main/java/com/example/maui/MainActivity.kt`, verify the flags match your environment:

#### Server Type (`activeServer`)
*   `ServerType.DEMO`: Connects to the `GATEWAY_URL` specified in `local.properties`. Use this for your Remote Server or the local Demo Server.
*   `ServerType.VANILLA`: Connects to a standalone Python agent running locally (e.g., `python -m my_agent --port 8000`).

#### Device Type (`deviceType`)
*   `DeviceType.PHYSICAL`: Use when testing on real Android devices. *(Note: If using a local Demo Server on a physical device, run `adb reverse tcp:10002 tcp:10002`)*
*   `DeviceType.EMULATOR`: Use when testing on emulators.

### 4. Build and Run the App

1. Navigate to the Android sample app directory:
   ```bash
   cd ~/ai-kit/a2ui-samples/client/android
   ```
2. Build and install the app (Debug version):
   ```bash
   ./gradlew :app:installDebug
   ```
   *(For release builds, use `./gradlew :app:installRelease`)*
3. Launch the app on your emulator or connected device:
   ```bash
   adb shell am start -n com.example.maui/.MainActivity
   ```

### 5. Example Prompts & Canned Responses

To facilitate rapid demonstration and UI testing, the sample app includes a dropdown list of frequently used example prompts.

**Important Behavior Note:**
*   **Example Prompts:** Selecting an example prompt from the dropdown menu will load a **local, pre-stored JSON response** (found in `assets/canned_responses`) instead of making a live call to the backend server. This is intended for consistent UI testing and fast demonstrations without LLM latency.

## Troubleshooting

### Debug Keystore Missing
If you receive an error: `Keystore file ... debug.keystore not found`, this means your local Android environment hasn’t generated a default debug key yet.

**Solution:**
*   **Option A (Recommended):** Open the project in **Android Studio** and let it perform a Gradle sync. This will automatically generate the keystore.
*   **Option B (Manual):** Run the following command to generate one:
    ```bash
    keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    ```
