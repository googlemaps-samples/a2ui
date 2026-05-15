# Google Maps Agentic UI Toolkit Samples

> **Note:** The toolkit is in **Experimental** status.

Welcome to the **Google Maps Agentic UI Toolkit Samples**! 🎉 

This repository (`a2ui-samples`) contains the reference demonstration for the Google Maps Agentic UI Toolkit. It provides a fully working, interactive sample application implementing the Agent-to-User Interface (A2UI) standard, allowing AI agents to present rich, dynamic map interfaces directly in your web browser.

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