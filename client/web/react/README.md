# Agentic UI Toolkit + React

Note: this example requires a valid Google Maps API Key to be set within [index.html](index.html). If you have a `GOOGLE_MAPS_API_KEY` environment variable set to a valid Google Maps API key, Vite will automatically inject it into [index.html](index.html) at build time.

## To run this sample project

1. Download the latest Agentic UI Toolkit source files.
2. Open the a2ui/client/web directory in a terminal and run:

```
npm run build-and-link
```

3. Open this directory in a terminal, and run:

```
npm install
npm link @googlemaps/a2ui
npm run dev
```

4. Open [http://localhost:5173](http://localhost:5173) in your browser. You should see a chat interface.

To run the backend, follow the instructions in [../../../agent/python/README.md](../../../agent/python/README.md)