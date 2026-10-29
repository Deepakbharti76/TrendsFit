import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const target = process.env.VITE_DEV_API || "http://localhost:8082";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": target,
      "/uploads": target,
      "/sitemap.xml": target,
      "/robots.txt": target,
    },
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 700,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.js"],
    css: false,
  },
});
