# Google Maps Agentic UI Toolkit Samples

![Alpha](https://img.shields.io/badge/release-alpha-orange)
[![Python CI](https://github.com/googlemaps/a2ui/actions/workflows/python-ci.yml/badge.svg)](https://github.com/googlemaps/a2ui/actions/workflows/python-ci.yml)
[![Web CI](https://github.com/googlemaps/a2ui/actions/workflows/web-ci.yml/badge.svg)](https://github.com/googlemaps/a2ui/actions/workflows/web-ci.yml)
[![GitHub License](https://img.shields.io/github/license/googlemaps-samples/a2ui?color=blue)]((https://github.com/googlemaps-samples/a2ui/LICENSE))

> **Note:** The toolkit is in **Experimental** status.

Welcome to the **Google Maps Agentic UI Toolkit Samples**! 🎉 

This repository (`a2ui-samples`) contains reference samples for the Google Maps Agentic UI Toolkit. It provides a fully working, interactive sample application implementing the Agent-to-User Interface (A2UI) standard, allowing AI agents to present rich, dynamic map interfaces directly in your web browser.

This project is intended for demonstration purposes to help you get up and running quickly!

## 🗺️ Repository Orientation

To run this demonstration successfully, you need both the core toolkit repository (`a2ui`) and this samples repository (`a2ui-samples`). **We highly recommend cloning both repositories side-by-side as sibling directories in the same parent folder.**

Here is an overview of how the directory structure looks when set up correctly:

```text
parent-folder/
├── a2ui/                           <-- SIBLING REPOSITORY (Core Toolkit)
│   ├── agent/python-agent/         <-- Core Python agent libraries (maui-a2ui-python)
│   └── client/web/                 <-- Core web UI component library (@googlemaps/a2ui)
│
└── a2ui-samples/                   <-- THIS REPOSITORY (Quickstart & Demos)
    ├── agent/python/               <-- Sample backend Python agent server
    └── client/web/react/           <-- Sample frontend React web application
```

Understanding this layout ensures you will feel entirely comfortable linking the backend and frontend components in the quickstart steps below!

## 🚀 Quickstart Guide

To build and see the initial demo working in your web browser, you will set up both the backend Python agent and the frontend React client. We have designed these instructions to be as concise as possible while providing all the details you need to succeed without frustration.

### Prerequisites & Tool Setup

If you are new to any of these tools or services, here is exactly what you need and how to set them up:

#### 1. Google API Keys
You need two API keys configured as environment variables for the agent to function and render maps correctly:

*   **`GEMINI_API_KEY`**: Your Google Gemini API key. You can get one for free at [Google AI Studio](https://aistudio.google.com/).
*   **`GOOGLE_MAPS_API_KEY`**: Your Google Maps Platform API key. You can create one and enable the Maps JavaScript API in the [Google Cloud Console](https://mapsplatform.google.com/).

These variables must be configured for _both_ your backend (Python agent) and frontend (Web frontend).

For more information about the environment variables, see the **Google API Key Configuration** below.

**How to set your environment variables:**

* **macOS / Linux (Terminal):**
  ```bash
  export GEMINI_API_KEY="your_gemini_api_key_here"
  export GOOGLE_MAPS_API_KEY="your_google_maps_api_key_here"
  ```
* **Windows (PowerShell):**
  ```powershell
  $env:GEMINI_API_KEY="your_gemini_api_key_here"
  $env:GOOGLE_MAPS_API_KEY="your_google_maps_api_key_here"
  ```
* **Windows (Command Prompt):**
  ```cmd
  set GEMINI_API_KEY=your_gemini_api_key_here
  set GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
  ```

#### 2. `uv` (Python Package Manager)
`uv` is an extremely fast Python package and environment manager. It automatically handles creating virtual environments and installing dependencies so you don't have to do it manually.
*   **Installation:** If you do not have `uv` installed, you can install it easily following the official guide at [https://docs.astral.sh/uv/](https://docs.astral.sh/uv/) (for example, on macOS/Linux run `curl -LsSf https://astral.sh/uv/install.sh | sh`).

#### 3. `Node.js` and `npm` (JavaScript Runtime & Package Manager)
`npm` is the standard package manager for JavaScript and TypeScript web applications, used to download frontend libraries and run development servers.
*   **Installation:** Download and install Node.js (which includes `npm`) from [https://nodejs.org/](https://nodejs.org/).

---

### Step 1: Run the Backend (Python Agent)

The backend server is powered by Python and runs our sample agent.

1.  **Navigate to the Python agent directory** from the repository root:
    ```bash
    cd a2ui-samples/agent/python
    ```

2.  **Link the core MAUI Python package:**
    The sample agent needs to know where the core `maui-a2ui-python` library is located. Assuming you cloned both `a2ui` and `a2ui-samples` as sibling directories, the core package is located at `../../../a2ui/agent/python-agent`.
    
    Run our helper script to automatically configure this path in `pyproject.toml`:
    ```bash
    chmod +x setup.sh
    ./setup.sh ../../../a2ui/agent/python-agent
    ```
    *(Note: If you cloned `a2ui` to a different location, simply replace `../../../a2ui/agent/python-agent` with the correct absolute or relative path to `a2ui/agent/python-agent`).*

3.  **Start the backend server:**
    ```bash
    uv run .
    ```
    `uv` will automatically create a virtual environment, install all required dependencies, and start the server on port `10002`.

---

### Step 2: Run the Frontend (React Client)

The frontend is a React web application that communicates with the backend agent and renders the interactive UI.

1.  **Build and link the core @googlemaps/a2ui library:**
    First, we need to compile the core web components in the `a2ui` repository and make them available locally via `npm link`. From your main parent folder, navigate to the core client directory:
    ```bash
    cd a2ui/client/web
    npm run build-and-link
    ```

2.  **Navigate to the sample React client directory:**
    ```bash
    cd ../../../a2ui-samples/client/web/react
    ```

3.  **Install dependencies, link the core library, and start the development server:**
    ```bash
    npm install
    npm link @googlemaps/a2ui
    npm run dev
    ```

4.  **See the demo working live!** 🌟
    Open [http://localhost:5173](http://localhost:5173) in your web browser to interact with your fully functioning Agentic UI demo!

## Google API Keys

### Google Maps API Key

Agentic UI Toolkit requires an API Key to use Google Maps Platform products. To create a Google Maps API Key, follow the instructions in the [Google Maps Platform documentation](https://developers.google.com/maps/documentation/javascript/get-api-key).

Your API Key must have the following APIs enabled in the [Google Cloud Console](https://console.cloud.google.com/apis/credentials):

* Geocoding API  
* Maps JavaScript API  
* Places UI Kit  
* Routes API

To use Grounding Lite MCP, you must also enable:

* Maps Grounding Lite API

To support the use of Grounding Lite within the Python ADK backend, this API Key must be exported or contained within a `.env` file as `GOOGLE_MAPS_API_KEY`.

**Loading the Google Maps JavaScript API**

Your API Key must also be included when loading the Google Maps JavaScript API code. See the [Google Maps Platform Documentation](https://developers.google.com/maps/documentation/javascript/load-maps-js-api) for instructions on how to load the API, including configuring the API Key.

Agentic UI Toolkit requires features available in the Alpha channel. You must use `v=alpha` when loading the Maps JavaScript API. Learn more about versions in the [Google Maps Platform Documentation](https://developers.google.com/maps/documentation/javascript/versions).

Use of Agentic UI Toolkit requires several [Maps JavaScript API libraries](https://developers.google.com/maps/documentation/javascript/libraries). When loading the Google Maps JavaScript API, you must include the following libraries:

* maps  
* maps3d  
* marker  
* places  
* routes

### Gemini API Key

*Note: This API is variously referred to in Google Cloud as the* Gemini API *and the* Generative Language API.

If you are using Gemini as your LLM, you will also need a Google Cloud API Key with the *Generative Language API* enabled. In order to enable this API for your API Key, the *Gemini API* must be enabled for your Google Cloud project. You can enable this API in the [API Library](https://console.cloud.google.com/apis/library/generativelanguage.googleapis.com).

To create a new Google Cloud API Key, follow the instructions here in the [Google Cloud docs](https://docs.cloud.google.com/docs/authentication/api-keys#create).

This key must be exported or contained within a `.env` file as `GEMINI_API_KEY`

## Google API Keys

### Google Maps API Key

Agentic UI Toolkit requires an API Key to use Google Maps Platform products. To create a Google Maps API Key, follow the instructions in the [Google Maps Platform documentation](https://developers.google.com/maps/documentation/javascript/get-api-key).

Your API Key must have the following APIs enabled in the [Google Cloud Console](https://console.cloud.google.com/apis/credentials):

* Geocoding API  
* Maps JavaScript API  
* Places UI Kit  
* Routes API

To use Grounding Lite MCP, you must also enable:

* Maps Grounding Lite API

To support the use of Grounding Lite within the Python ADK backend, this API Key must be exported or contained within a `.env` file as `GOOGLE_MAPS_API_KEY`.

**Loading the Google Maps JavaScript API**

Your API Key must also be included when loading the Google Maps JavaScript API code. See the [Google Maps Platform Documentation](https://developers.google.com/maps/documentation/javascript/load-maps-js-api) for instructions on how to load the API, including configuring the API Key.

Agentic UI Toolkit requires features available in the Alpha channel. You must use `v=alpha` when loading the Maps JavaScript API. Learn more about versions in the [Google Maps Platform Documentation](https://developers.google.com/maps/documentation/javascript/versions).

Use of Agentic UI Toolkit requires several [Maps JavaScript API libraries](https://developers.google.com/maps/documentation/javascript/libraries). When loading the Google Maps JavaScript API, you must include the following libraries:

* maps  
* maps3d  
* marker  
* places  
* routes

### Gemini API Key

*Note: This API is variously referred to in Google Cloud as the* Gemini API *and the* Generative Language API.

If you are using Gemini as your LLM, you will also need a Google Cloud API Key with the *Generative Language API* enabled. In order to enable this API for your API Key, the *Gemini API* must be enabled for your Google Cloud project. You can enable this API in the [API Library](https://console.cloud.google.com/apis/library/generativelanguage.googleapis.com).

To create a new Google Cloud API Key, follow the instructions here in the [Google Cloud docs](https://docs.cloud.google.com/docs/authentication/api-keys#create).

This key must be exported or contained within a `.env` file as `GEMINI_API_KEY`

## Accessing Google Maps grounding data

Your agent can access Google Maps grounding data in two ways, depending on your project setup and needs: 

1. [Grounding Lite MCP](https://developers.google.com/maps/ai/grounding-lite)
2. [Grounding with Google Maps](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/grounding/grounding-with-google-maps)

### Grounding Lite MCP

To use Grounding Lite MCP, you must first enable the Maps Grounding Lite API and create or update an API Key to support the required APIs following the [documentation](https://developers.google.com/maps/ai/grounding-lite#configure_llms_to_use_the_mcp_server).

### Grounding with Google Maps

To use Grounding with Google Maps, there are additional steps you must take to configure your environment:

1. Ensure you have the latest version of the genai python package.
```bash
pip install --upgrade google-genai
```

2. Configure additional environment variables to connect to your project.
```bash
## Replace the `GOOGLE_CLOUD_PROJECT` and `GOOGLE_CLOUD_LOCATION` values
## with appropriate values for your project.
export GOOGLE_CLOUD_PROJECT=GOOGLE_CLOUD_PROJECT
export GOOGLE_CLOUD_LOCATION=global
export GOOGLE_GENAI_USE_VERTEXAI=True
```

3. Ensure you are authenticated to Google Cloud.
```bash
gcloud auth application-default login
```

See the [documentation](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/grounding/grounding-with-google-maps#googlegenaisdk_tools_google_maps_with_txt-python_genai_sdk) for more information.

## Contributing

External contributions are not accepted for this repository. See [contributing guide] for more info.

## Terms of Service

This library uses Google Maps Platform services. Use of Google Maps Platform services through this library is subject to the Google Maps Platform [Terms of Service].

This library is not a Google Maps Platform Core Service. Therefore, the Google Maps Platform Terms of Service (e.g. Technical Support Services, Service Level Agreements, and Deprecation Policy) do not apply to the code in this library.

## Support

This library is offered via an open source [license]. It is not governed by the Google Maps Platform Support [Technical Support Services Guidelines, the SLA, or the [Deprecation Policy]. However, any Google Maps Platform services used by the library remain subject to the Google Maps Platform Terms of Service.

[contributing guide]: CONTRIBUTING.md
[license]: LICENSE
[Deprecation Policy]: https://cloud.google.com/maps-platform/terms
[Technical Support Services Guidelines]: https://cloud.google.com/maps-platform/terms/tssg
[Terms of Service]: https://cloud.google.com/maps-platform/terms
