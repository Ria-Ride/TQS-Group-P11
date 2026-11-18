import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// In development (npm run dev) Vite forwards /api to a backend on localhost:8080,
// the same path rule nginx applies in the container.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
  test: {
    // A simulated browser (DOM, sessionStorage) so components can be tested without a real one.
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    restoreMocks: true,
    // Mocked API functions start every test with no programmed answer.
    mockReset: true,
  },
});