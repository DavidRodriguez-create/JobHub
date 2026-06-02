# JobHub UI

The JobHub frontend — a Vite + React app implementing the JobHub design
(see the design handoff: Job Search, Applications, Dashboard, Saved, Settings, Auth).

Runs on **mock data** by default (`src/data/mockData.js`). A real backend API
layer lives in `src/api/` and is enabled with the `VITE_USE_API` flag; when on,
Job Search loads from `job-service`. The remaining screens stay on mock until the
backend catches up — see **[BACKEND_GAPS.md](BACKEND_GAPS.md)** for exactly what's
missing and the suggested order to wire it.

The Vite dev server and nginx prod config reverse-proxy each service by path
prefix: `/auth` → auth-service, `/jobs` → job-service,
`/applications` + `/user-job-posts` → application-service.

### Backend integration

```bash
# Mock mode (default) — no backend needed
npm run dev

# Live mode — talk to the real services (must be running + reachable)
VITE_USE_API=true npm run dev
# override service targets if they aren't on the default ports:
#   VITE_AUTH_TARGET=http://localhost:8082 \
#   VITE_JOBS_TARGET=http://localhost:8081 \
#   VITE_APPS_TARGET=http://localhost:8083 VITE_USE_API=true npm run dev
```

`src/api/`: `client.js` (fetch wrapper + JWT), `config.js` (the flag),
`auth.js` / `jobs.js` / `applications.js` (endpoints), `mappers.js`
(backend DTO → UI shape; flags every synthesized field).

## Stack

- **Vite 5** + **React 18** (`@vitejs/plugin-react`, automatic JSX runtime)
- Design tokens in `src/styles/colors_and_type.css`, app styles in `src/styles/styles.css`
- No router lib — a single `route` state in `src/App.jsx` swaps screens (sidebar nav)

## Layout

```
src/
  main.jsx                  — entry; mounts <App/>, imports global CSS
  App.jsx                   — shell: routing, auth state, apply/save flows, modals
  data/mockData.js          — static jobs/applications/companies + helpers (DATA)
  api/                      — backend client, endpoints, mappers (see above)
  components/
    Icon.jsx                — inline Lucide-derived SVG icons
    ui.jsx                  — Button, Input, Field, Toggle, StatusPill, CoLogo,
                              Avatar, Sidebar, Topbar, Card, Stat, Modal, Toasts,
                              Empty, Tabs, JobRow
    FilterComponents.jsx    — DualRangeSlider, MultiSelect, SavedFiltersDropdown
    CommandPalette.jsx      — ⌘K contextual search overlay
    AddApplication.jsx      — "add manual application" modal
  screens/
    JobSearch.jsx           — filterable job list + job detail drawer
    Applications.jsx        — kanban/list + detail + status pipeline picker
    Dashboard.jsx           — pipeline bar, funnel, stats, urgent/awaiting/stale
    SavedSettings.jsx       — saved jobs + settings (account/notifications/…)
    Auth.jsx                — full-page login/signup + contextual login modal
```

> The prototype's design-tool "tweaks" panel (accent/density/demo switches) is
> intentionally omitted from the app build.

## Develop

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # → dist/
npm run preview  # serve the production build locally
```

> Responsive: the sidebar collapses to an off-canvas drawer (hamburger in the
> topbar) below 900px; grids stack down through 1100/1024/900/820/760/600px.

## Container

Multi-stage build (Node build → nginx serve):

```bash
podman build -t jobhub-ui:latest .
podman run --rm -p 3000:80 jobhub-ui:latest   # http://localhost:3000
```
