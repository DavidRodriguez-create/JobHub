import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server reverse-proxies each backend service by path prefix, mirroring
// nginx.conf in prod. Override targets with env vars when services run elsewhere.
const AUTH = process.env.VITE_AUTH_TARGET || "http://localhost:8082";
const JOBS = process.env.VITE_JOBS_TARGET || "http://localhost:8081";
const APPS = process.env.VITE_APPS_TARGET || "http://localhost:8083";
const NOTIFICATIONS = process.env.VITE_NOTIFICATIONS_TARGET || "http://localhost:8084";

// In a container the source is bind-mounted, where inotify file events don't
// propagate (Windows/WSL/podman) — Vite then serves stale modules. Polling fixes
// HMR there; enable it via VITE_POLL=true (set in podman-compose.yml).
const POLL = process.env.VITE_POLL === "true";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    watch: POLL ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      "/auth": { target: AUTH, changeOrigin: true },
      "/jobs": { target: JOBS, changeOrigin: true },
      "/applications": { target: APPS, changeOrigin: true },
      "/notifications": { target: NOTIFICATIONS, changeOrigin: true },
    },
  },
});
