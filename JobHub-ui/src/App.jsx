// JobHub — Main app shell with routing, auth state, and apply flow
import React from "react";
import DATA from "./data/mockData.js";
import Icon from "./components/Icon.jsx";
import { Sidebar, ToastTray, MobileNavContext } from "./components/ui.jsx";
import { JobSearchScreen, JobDetailDrawer } from "./screens/JobSearch.jsx";
import { ApplicationsScreen, ApplicationDetailScreen } from "./screens/Applications.jsx";
import { DashboardScreen } from "./screens/Dashboard.jsx";
import { SavedScreen, SettingsScreen } from "./screens/SavedSettings.jsx";
import { LoginScreen, SignUpScreen, LoginModal } from "./screens/Auth.jsx";
import { CommandPalette } from "./components/CommandPalette.jsx";
import { AddApplicationModal } from "./components/AddApplication.jsx";
import { USE_API } from "./api/config.js";
import { searchJobs, listSavedJobs, saveJob, unsaveJob } from "./api/jobs.js";
import {
  listApplications, getApplication, createApplication, updateApplication, updateApplicationJob,
  updateApplicationStatus, deleteApplication, applicationStats,
} from "./api/applications.js";
import { jobFromApi, savedJobFromApi, appFromApi, statusToApi } from "./api/mappers.js";
import { login, register, logout, currentUser } from "./api/auth.js";
import { getToken, clearToken } from "./api/client.js";

// Static presentation config (the design-tool "tweaks" panel is omitted in the app build).
const APP_CONFIG = {
  accentColor: "#2950E3",
  density: "balanced",
  startLoggedIn: false,
};

function App() {
  const t = APP_CONFIG;
  const [authed, setAuthed] = React.useState(t.startLoggedIn);
  const [account, setAccount] = React.useState(null); // the real signed-in account (never a demo identity)
  const [authScreen, setAuthScreen] = React.useState("login"); // login | signup

  // Apply accent color + derived shades
  React.useEffect(() => {
    const r = document.documentElement;
    r.style.setProperty("--color-brand-600", t.accentColor);
    const hex = t.accentColor;
    r.style.setProperty("--color-brand-700", adjustBrightness(hex, -15));
    r.style.setProperty("--color-brand-800", adjustBrightness(hex, -30));
    r.style.setProperty("--color-brand-50", adjustBrightness(hex, 90));
    r.style.setProperty("--color-brand-100", adjustBrightness(hex, 75));
    r.style.setProperty("--color-brand-200", adjustBrightness(hex, 55));
  }, [t.accentColor]);

  React.useEffect(() => {
    const r = document.documentElement;
    if (t.density === "compact") r.style.fontSize = "13px";
    else if (t.density === "comfortable") r.style.fontSize = "15px";
    else r.style.fontSize = "14px";
    return () => { r.style.fontSize = ""; };
  }, [t.density]);

  const [route, setRoute] = React.useState("search");
  const [openAppData, setOpenAppData] = React.useState(null);
  const [selectedJob, setSelectedJob] = React.useState(null);
  const [loginModalJob, setLoginModalJob] = React.useState(null);
  const [authPromptReason, setAuthPromptReason] = React.useState(null);
  const [showAddApp, setShowAddApp] = React.useState(false);
  const [savedIds, setSavedIds] = React.useState(() => new Set(DATA.saved));
  const [appliedJobIds, setAppliedJobIds] = React.useState(() => {
    return new Set(DATA.applications.map((a) => a.jobId));
  });
  const [toasts, setToasts] = React.useState([]);
  const [cmdPaletteOpen, setCmdPaletteOpen] = React.useState(false);
  const [mobileNavOpen, setMobileNavOpen] = React.useState(false);
  // Bumped whenever the DATA store is re-hydrated from the API, to re-render the
  // screens that read the singleton synchronously.
  const [, setDataVersion] = React.useState(0);
  const bumpData = React.useCallback(() => setDataVersion((v) => v + 1), []);
  const [stats, setStats] = React.useState(null); // ApplicationStatsResponse for the dashboard
  const mobileNav = React.useMemo(() => ({ openNav: () => setMobileNavOpen(true) }), []);

  // Load the authenticated user's real applications + saved jobs into the store.
  const loadUserData = React.useCallback(async () => {
    if (!USE_API) return;
    try {
      const { items } = await listApplications({ page: 0, size: 100 });
      const apps = items.map((a) => appFromApi(a, DATA)).filter(Boolean);
      DATA.applications.length = 0;
      DATA.applications.push(...apps);
      setAppliedJobIds(new Set(apps.map((a) => a.jobId)));
    } catch (e) { /* leave applications empty on failure */ }
    try {
      const { items } = await listSavedJobs({ page: 0, size: 100 });
      const ids = items.map((s) => {
        // The saved-jobs endpoint returns the FULL job posting. Upsert (replace), so it
        // wins over any bare placeholder an applied-to job left behind earlier in this
        // load — otherwise the drawer shows "Careers"/"No description" instead of real data.
        const job = savedJobFromApi(s, DATA.companies);
        const idx = DATA.jobs.findIndex((j) => j.id === job.id);
        if (idx >= 0) DATA.jobs[idx] = job; else DATA.jobs.push(job);
        return job.id;
      });
      DATA.saved.length = 0;
      DATA.saved.push(...ids);
      setSavedIds(new Set(ids));
    } catch (e) { /* leave saved empty on failure */ }
    try {
      setStats(await applicationStats());
    } catch (e) { /* leave stats null on failure */ }
    bumpData();
  }, [bumpData]);

  function clearUserData() {
    DATA.applications.length = 0;
    DATA.saved.length = 0;
    setStats(null);
    setAppliedJobIds(new Set());
    setSavedIds(new Set());
    bumpData();
  }

  // When VITE_USE_API is on, hydrate the public job list from job-service before the
  // screens (which read the DATA singleton) first render, then restore the session and
  // the user's applications/saved. With the API off the store stays empty (no mock data).
  const [booting, setBooting] = React.useState(USE_API);
  React.useEffect(() => {
    if (!USE_API) return;
    let cancelled = false;
    (async () => {
      try {
        const { items } = await searchJobs({ page: 0, size: 100 });
        if (!cancelled) {
          const mapped = items.map((dto) => jobFromApi(dto, DATA.companies));
          DATA.jobs.length = 0;
          DATA.jobs.push(...mapped);
        }
      } catch (e) {
        if (!cancelled) pushToast("Couldn't reach job-service — job search is unavailable.", "info");
      }
      // Restore a session only if a stored JWT is still valid; otherwise stay logged out.
      try {
        if (getToken()) {
          const acc = await currentUser();
          if (!cancelled) {
            setAccount(acc);
            setAuthed(true);
            await loadUserData();
          }
        }
      } catch {
        clearToken();
        if (!cancelled) { setAccount(null); setAuthed(false); }
      } finally {
        if (!cancelled) setBooting(false);
      }
    })();
    return () => { cancelled = true; };
  }, [loadUserData]);

  // ⌘K keyboard shortcut
  React.useEffect(() => {
    const h = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setCmdPaletteOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, []);

  // Persist route in sessionStorage
  React.useEffect(() => {
    try {
      const saved = sessionStorage.getItem("jobhub_route");
      if (saved) setRoute(saved);
    } catch {}
  }, []);
  React.useEffect(() => {
    try { sessionStorage.setItem("jobhub_route", route); } catch {}
  }, [route]);

  function pushToast(text, icon = "check", action) {
    const id = Math.random().toString(36).slice(2);
    setToasts((t) => [...t, { id, text, icon, action }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), action ? 4000 : 2400);
  }

  // Real authentication: validate credentials against auth-service and store the JWT.
  // In standalone mock mode (USE_API=false) we only require non-empty fields so the
  // demo still works without a backend — but an empty form never logs you in.
  async function handleLogin(email, password) {
    let acc = null;
    if (USE_API) {
      const res = await login({ email, password }); // throws ApiError (401) on invalid credentials
      acc = res?.account || null;
    } else {
      if (!email || !password) throw new Error("Enter your email and password.");
      acc = { email };
    }
    completeLogin(acc, "Welcome back.");
  }

  async function handleSignup(name, email, password) {
    let acc = null;
    const parts = (name || "").trim().split(/\s+/).filter(Boolean);
    const firstName = parts.shift() || "";
    const lastName = parts.join(" ") || "";
    if (USE_API) {
      acc = await register({ firstName, lastName, email, password });
      const res = await login({ email, password });
      acc = res?.account || acc;
    } else {
      if (!name || !email || !password) throw new Error("Fill in all fields.");
      acc = { firstName, lastName, email };
    }
    // A brand-new account lands on the public job search page.
    completeLogin(acc, "Account created.", "search");
  }

  function completeLogin(acc, toast, forcedRoute) {
    setAccount(acc || null);
    setAuthed(true);
    pushToast(toast || "Welcome back.");
    loadUserData(); // pull the user's real applications + saved jobs
    if (loginModalJob) {
      const job = loginModalJob;
      setLoginModalJob(null);
      setTimeout(() => handleApply(job, true), 300);
      return;
    }
    if (authPromptReason) {
      const dest = authPromptReason;
      setAuthPromptReason(null);
      setTimeout(() => { setRoute(dest); }, 100);
      return;
    }
    // Default landing (and after signup): the public job search page.
    setRoute(forcedRoute || "search");
  }

  function handleLogout() {
    logout(); // drop the JWT
    setAccount(null);
    setAuthed(false);
    clearUserData(); // drop the user's applications/saved from the store
    setRoute("search");
    setOpenAppData(null);
    pushToast("Signed out.", "logout");
  }

  function handleSaveToggle(job) {
    if (!authed) {
      setLoginModalJob(job);
      return;
    }
    const willSave = !savedIds.has(job.id);
    setSavedIds((prev) => {
      const next = new Set(prev);
      if (next.has(job.id)) {
        next.delete(job.id);
        pushToast("Removed from saved.", "x", {
          label: "Undo",
          fn: () => setSavedIds((p) => new Set([...p, job.id])),
        });
      } else {
        next.add(job.id);
        pushToast("Saved.", "bookmark");
      }
      return next;
    });
    if (USE_API) {
      (willSave ? saveJob(job.id) : unsaveJob(job.id)).catch(() => {});
    }
  }

  function handleApply(job, skipAuthCheck) {
    if (!authed && !skipAuthCheck) {
      setLoginModalJob(job);
      return;
    }
    if (appliedJobIds.has(job.id)) {
      pushToast(`You've already applied to ${DATA.coOf(job.co).name}.`, "info");
      return;
    }
    setAppliedJobIds((prev) => new Set([...prev, job.id]));
    pushToast(`Applied to ${DATA.coOf(job.co).name}. Opening posting…`, "send");
    setSelectedJob(null);
    if (USE_API) {
      createApplication({ jobPostId: job.id })
        .then(() => loadUserData())
        .catch((e) => {
          // 409 = an application already exists for this job; keep it marked applied
          // and re-sync. Any other failure: roll back the optimistic "Applied" badge.
          if (e && e.status === 409) {
            loadUserData();
            return;
          }
          setAppliedJobIds((prev) => {
            const next = new Set(prev);
            next.delete(job.id);
            return next;
          });
          pushToast("Couldn't submit the application — please try again.", "info");
        });
    }
  }

  function handleAddManualApp(formData) {
    if (USE_API) {
      setShowAddApp(false);
      (async () => {
        try {
          const created = await createApplication({ jobDetails: {
            title: formData.title,
            company: formData.company,
            url: formData.postUrl || undefined,
            location: formData.location,
          }});
          const patch = {};
          if (formData.notes) patch.notes = formData.notes;
          if (formData.portalUrl) patch.portalUrl = formData.portalUrl;
          if (formData.appliedOn) patch.appliedAt = new Date(formData.appliedOn).toISOString();
          if (Object.keys(patch).length) await updateApplication(created.id, patch);
          const uiStatus = formData.status || "applied";
          if (uiStatus !== "applied") await updateApplicationStatus(created.id, statusToApi(uiStatus));
          await loadUserData();
          pushToast(`Application added for ${formData.company}.`, "check");
        } catch (e) {
          pushToast("Couldn't save the application to the server.", "info");
        }
      })();
      goto("applications");
      return;
    }

    // Standalone (no API): keep the application in the in-memory store for the session.
    const coKey = formData.company.toLowerCase().replace(/[^a-z]/g, "").slice(0, 12) || "custom";
    const jobId = "J-CUSTOM-" + Date.now();
    const appId = DATA.nextAppId();
    const today = new Date().toISOString().slice(0, 10);

    if (!DATA.companies[coKey]) {
      DATA.companies[coKey] = { name: formData.company, industry: "—", size: "—", hq: "—", url: "" };
    }
    DATA.jobs.push({
      id: jobId, co: coKey, title: formData.title, location: formData.location,
      comp: formData.comp, compMin: 0, compMax: 999, type: "Full-time", postedDays: 0,
      source: "Manual", remote: false, tags: [], country: "—", language: "English",
      desc: formData.notes || "Manually added application.", reqs: [],
    });
    DATA.applications.unshift({
      id: appId, jobId: jobId, status: formData.status || "applied",
      appliedOn: formData.appliedOn || today, lastUpdate: formData.appliedOn || today,
      portalUrl: formData.portalUrl || "", postUrl: formData.postUrl || "",
      contact: "", notes: formData.notes || "",
      nextStep: "—",
      timeline: [{ date: formData.appliedOn || today, what: "Applied (added manually)" }],
    });

    setAppliedJobIds((prev) => new Set([...prev, jobId]));
    setShowAddApp(false);
    pushToast(`Application added for ${formData.company}.`, "check");
    goto("applications");
  }

  function handleDeleteApp(app) {
    const j = DATA.byId(app.jobId);
    const c = j ? DATA.coOf(j.co) : { name: "this role" };
    const idx = DATA.applications.findIndex((a) => a.id === app.id);
    if (idx > -1) DATA.applications.splice(idx, 1);
    setAppliedJobIds((prev) => {
      const next = new Set(prev);
      next.delete(app.jobId);
      return next;
    });
    setOpenAppData(null);
    setRoute("applications");
    pushToast(`Deleted application for ${c.name}.`, "trash");
    if (USE_API && app.apiId) {
      deleteApplication(app.apiId).then(() => loadUserData()).catch(() => {});
    }
  }

  function handleOpenApp(app) {
    setOpenAppData(app);   // show immediately (list object has no timeline)
    setRoute("application");
    if (USE_API && app?.apiId) {
      refreshOpenApp(app.apiId); // fetch the detail (with timeline) and swap it in
    }
  }

  // ── Persist application-detail edits to the API ─────────────────────────────
  // The detail screen also mutates the in-memory object for instant feedback; these
  // push the change to the backend and re-sync from the server (so the timeline,
  // which the server appends on each status change, reflects the canonical state).
  // Re-point the open detail card at the freshest data. The single-application GET
  // includes the timeline (the list response omits it), so fetch the detail when
  // possible and fall back to the list-derived object otherwise.
  async function refreshOpenApp(apiId) {
    if (USE_API) {
      try {
        const dto = await getApplication(apiId);
        setOpenAppData(appFromApi(dto, DATA));
        return;
      } catch (e) { /* fall back to the store copy below */ }
    }
    setOpenAppData(DATA.applications.find((a) => a.apiId === apiId) || null);
  }

  async function persistStatusChange(app, uiStatus) {
    if (!USE_API || !app?.apiId) return;
    try {
      await updateApplicationStatus(app.apiId, statusToApi(uiStatus));
      await loadUserData();
      await refreshOpenApp(app.apiId);
    } catch (e) {
      pushToast("Couldn't save the status change to the server.", "info");
    }
  }

  async function persistNotes(app, notes) {
    if (!USE_API || !app?.apiId) return;
    try {
      await updateApplication(app.apiId, { notes });
    } catch (e) {
      pushToast("Couldn't save your notes to the server.", "info");
    }
  }

  async function persistEdit(app, data) {
    if (!USE_API || !app?.apiId) return;
    try {
      const patch = {};
      if (data.contact !== undefined) patch.contact = data.contact;
      if (data.portalUrl) patch.portalUrl = data.portalUrl;
      if (data.appliedOn) patch.appliedAt = new Date(data.appliedOn + "T00:00:00").toISOString();
      if (Object.keys(patch).length) await updateApplication(app.apiId, patch);
      // Job details only apply to manual entries; crawled-job snapshots are immutable (409).
      try {
        await updateApplicationJob(app.apiId, {
          title: data.title, url: data.postUrl || undefined, location: data.location,
        });
      } catch (jobErr) { /* crawled-job application → 409, leave the snapshot as-is */ }
      await loadUserData();
      await refreshOpenApp(app.apiId);
    } catch (e) {
      pushToast("Couldn't save the changes to the server.", "info");
    }
  }

  function handleOpenJob(job) {
    setSelectedJob(job);
  }

  function goto(r) {
    setMobileNavOpen(false);
    if ((r === "applications" || r === "dashboard" || r === "saved" || r === "settings") && !authed) {
      setLoginModalJob(null);
      setAuthPromptReason(r);
      return;
    }
    setOpenAppData(null);
    setSelectedJob(null);
    setAuthPromptReason(null);
    setRoute(r);
    if (r === "login") {
      setAuthScreen("login");
    }
  }

  if (booting) {
    return <LoadingScreen />;
  }

  // Auth screens (full page). Shown for the login/signup routes and as a hard gate
  // in front of any protected route reached while logged out (e.g. a protected route
  // restored from sessionStorage on reload).
  const PROTECTED_ROUTES = new Set(["dashboard", "applications", "application", "saved", "settings"]);
  if (!authed && (route === "login" || route === "signup" || PROTECTED_ROUTES.has(route))) {
    if (authScreen === "signup" || route === "signup") {
      return <SignUpScreen onSignUp={handleSignup} onSwitch={() => setAuthScreen("login")} />;
    }
    return <LoginScreen onLogin={handleLogin} onSwitch={() => setAuthScreen("signup")} />;
  }

  const appCounts = {
    total: DATA.applications.length,
    interview: DATA.applications.filter((a) => a.status === "interview").length,
    applied: DATA.applications.filter((a) => a.status === "applied").length,
  };

  // Only expose applied/saved state when authenticated
  const visibleAppliedIds = authed ? appliedJobIds : new Set();
  const visibleSavedIds = authed ? savedIds : new Set();

  const openSearch = () => setCmdPaletteOpen(true);

  const screenMap = {
    dashboard:    <DashboardScreen goto={goto} openApp={handleOpenApp} openSearch={openSearch} onAddApp={() => setShowAddApp(true)} stats={stats} />,
    search:       <JobSearchScreen goto={goto} onSaveToggle={handleSaveToggle} savedIds={visibleSavedIds} openJob={handleOpenJob} appliedJobIds={visibleAppliedIds} authed={authed} openSearch={openSearch} />,
    applications: <ApplicationsScreen openApp={handleOpenApp} openSearch={openSearch} onAddApp={() => setShowAddApp(true)} />,
    application:  openAppData && <ApplicationDetailScreen app={openAppData} goto={goto} onBack={() => goto("applications")} openSearch={openSearch} onDelete={handleDeleteApp} onStatusChange={persistStatusChange} onNotesSave={persistNotes} onEditSave={persistEdit} />,
    saved:        <SavedScreen savedIds={visibleSavedIds} onSaveToggle={handleSaveToggle} openJob={handleOpenJob} goto={goto} appliedJobIds={visibleAppliedIds} openSearch={openSearch} />,
    settings:     <SettingsScreen authed={authed} account={account} onLogout={handleLogout} onLogin={() => { setRoute("login"); setAuthScreen("login"); }} openSearch={openSearch} />,
  };

  const sidebarCurrent = route === "application" ? "applications" : route;

  return (
    <MobileNavContext.Provider value={mobileNav}>
    <div className="app">
      <Sidebar current={sidebarCurrent} onNav={goto} appCounts={authed ? appCounts : { total: 0, interview: 0, applied: 0 }} savedCount={authed ? savedIds.size : 0} authed={authed} account={account} mobileOpen={mobileNavOpen} onClose={() => setMobileNavOpen(false)} />
      <main className="main">
        {screenMap[route] || screenMap.search}
      </main>

      {/* Job detail drawer */}
      {selectedJob && (
        <JobDetailDrawer
          job={selectedJob}
          onClose={() => setSelectedJob(null)}
          onApply={handleApply}
          onSave={handleSaveToggle}
          isSaved={authed && savedIds.has(selectedJob.id)}
          isApplied={authed && appliedJobIds.has(selectedJob.id)}
          authed={authed}
        />
      )}

      {/* Login modal (triggered when applying without auth or accessing protected pages) */}
      {loginModalJob && (
        <LoginModal
          onClose={() => setLoginModalJob(null)}
          onLogin={handleLogin}
          onSignUp={handleSignup}
          jobTitle={`${loginModalJob.title} at ${DATA.coOf(loginModalJob.co).name}`}
        />
      )}
      {authPromptReason && !loginModalJob && (
        <LoginModal
          onClose={() => setAuthPromptReason(null)}
          onLogin={handleLogin}
          onSignUp={handleSignup}
          reason={authPromptReason}
        />
      )}

      {/* Add application modal */}
      {showAddApp && (
        <AddApplicationModal
          onClose={() => setShowAddApp(false)}
          onSubmit={handleAddManualApp}
        />
      )}

      <ToastTray toasts={toasts} />

      {/* Command palette */}
      {cmdPaletteOpen && (
        <CommandPalette
          mode={route}
          onClose={() => setCmdPaletteOpen(false)}
          onSelectJob={handleOpenJob}
          onSelectApp={handleOpenApp}
        />
      )}
    </div>
    </MobileNavContext.Provider>
  );
}

// Animated boot loader: a commuter walks into the building, reappears on the
// other side, and walks back in — looping while the app hydrates.
function LoadingScreen() {
  return (
    <div className="loading-screen">
      <div className="loading-stage">
        <span className="loading-building"><Icon name="building" size={36} /></span>
        <span className="loading-walker">
          <span className="loading-walker-step"><Icon name="user" size={26} /></span>
        </span>
      </div>
      <div className="loading-label">Loading jobs…</div>
    </div>
  );
}

// Simple brightness adjustment for hex colors
function adjustBrightness(hex, pct) {
  hex = hex.replace("#", "");
  let r = parseInt(hex.substring(0, 2), 16);
  let g = parseInt(hex.substring(2, 4), 16);
  let b = parseInt(hex.substring(4, 6), 16);
  if (pct > 0) {
    r = Math.round(r + (255 - r) * (pct / 100));
    g = Math.round(g + (255 - g) * (pct / 100));
    b = Math.round(b + (255 - b) * (pct / 100));
  } else {
    const f = 1 + pct / 100;
    r = Math.round(r * f); g = Math.round(g * f); b = Math.round(b * f);
  }
  return "#" + [r, g, b].map(c => Math.min(255, Math.max(0, c)).toString(16).padStart(2, "0")).join("");
}

export default App;
