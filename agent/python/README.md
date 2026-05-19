# Agentic UI Toolkit - Python Agent

This is a sample Python agent that consumes the MAUI packages and provides a backend for the A2UI chat interface.

## Prerequisites

**Source code:**
*   Download/clone the Agentic UI Toolkit source code from [GitHub](https://github.com/googlemaps/a2ui)

**Environment variables:**
This example requires the following environment variables to be set:
*   `GEMINI_API_KEY`: Your Gemini API key.
*   `GOOGLE_MAPS_API_KEY`: Your Google Maps API key (used by the agent for location-based queries).

**Tools:**
*   `uv`: Python package manager and runner. Install from https://docs.astral.sh/uv/


## To run this sample project

1.  Open this directory in a terminal.
2.  Set the path to the MAUI package in [pyproject.toml](pyproject.toml).
    
    You can either do this manually by replacing the `$MAUI_PATH` placeholder in [pyproject.toml](pyproject.toml)
    with the path to the MAUI package, or by running the [setup.sh](setup.sh) script:

    ```bash
    chmod +x setup.sh
    ./setup.sh <path_to_maui_package>
    ```
3.  Run the following command to start the server:

    ```bash
    uv run .
    ```

    This will automatically resolve dependencies, install them in a local virtual environment, and start the A2A server on port 10002.

To run the frontend, follow the instructions in [../../client/web/react/README.md](../../client/web/react/README.md)

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
