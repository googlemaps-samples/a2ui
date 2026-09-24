# Agentic UI Toolkit + React

Note: this example requires a valid Google Maps API Key to be set within [index.html](index.html). If you have a `GOOGLE_MAPS_API_KEY` environment variable set to a valid Google Maps API key, Vite will automatically inject it into [index.html](index.html) at build time.

## To run this sample project

1. Open this directory in a terminal, and run:

```
npm install
npm run dev
```

2. Open [http://localhost:5173](http://localhost:5173) in your browser. You should see a chat interface.

## Streaming vs. Non-Streaming (`useStreaming`)

By default, the React sample streams incremental text and A2UI component updates via `client.sendStream()` (`DEFAULT_USE_STREAMING = true` in `src/App.tsx`). You can switch between streaming (`client.sendStream()`) and non-streaming (`client.send()`) at any time using the **Streaming** checkbox in the chat header or by modifying `DEFAULT_USE_STREAMING` in `src/App.tsx`.

To run the backend, follow the instructions in [../../../agent/python/README.md](../../../agent/python/README.md)
