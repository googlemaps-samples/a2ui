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
