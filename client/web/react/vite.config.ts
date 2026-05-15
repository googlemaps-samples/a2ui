import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), {
    name: "html-transform",
    transformIndexHtml(html) {
      return html.replace(
        "$GOOGLE_MAPS_API_KEY",
        process.env.GOOGLE_MAPS_API_KEY || ""
      ).replace(
        "$SERVER_URL",
        process.env.SERVER_URL || "http://localhost:10002"
      );
    },
  },],
})