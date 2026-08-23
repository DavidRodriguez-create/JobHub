// JobHub — Main app shell with routing, auth state, and apply flow
import React from "react";
import DATA from "./data/mockData.js";
import Icon from "./components/Icon.jsx";
import { Sidebar, ToastTray, MobileNavContext, Button, Empty } from "./components/ui.jsx";
import { JobSearchScreen, JobDetailDrawer } from "./screens/JobSearch.jsx";
import { ApplicationsScreen, ApplicationDetailScreen } from "./screens/Applications.jsx";
import { DashboardScreen } from "./screens/Dashboard.jsx";
import { SavedScreen, SettingsScreen } from "./screens/SavedSettings.jsx";
import { NotificationsScreen } from "./screens/Notifications.jsx";
import {
  LoginScreen, SignUpScreen, LoginModal, VerifyEmailScreen, TwoFactorLoginStep, OAuthCallbackScreen,
  HIDE_ALL_PROVIDERS, SHOW_ALL_PROVIDERS,
} from "./screens/Auth.jsx";
import { AdminPage } from "./screens/AdminPage.jsx";
import { AdminCompaniesPage } from "./screens/AdminCompanies.jsx";
import { CommandPalette } from "./components/CommandPalette.jsx";
import { AddApplicationModal } from "./components/AddApplication.jsx";
import { ApplyProfileDrawer } from "./components/applyProfile/ApplyProfileDrawer.jsx";
import { prefetchApplyProfile, clearApplyProfileCache } from "./components/applyProfile/applyProfileCache.js";
import { USE_API } from "./api/config.js";
import { searchJobs, listSavedJobs, saveJob, unsaveJob } from "./api/jobs.js";
import {
  listApplications, getApplication, createApplication, updateApplication, updateApplicationJob,
  updateApplicationStatus, deleteApplication, applicationStats,
} from "./api/applications.js";
import { jobFromApi, savedJobFromApi, appFromApi, statusToApi } from "./api/mappers.js";
import { login, loginTwoFactor, register, logout, currentUser, resendVerification, getOAuthProviders } from "./api/auth.js";
import { getToken, clearToken } from "./api/client.js";
import { getUnreadCount } from "./api/notifications.js";
import { clearQueryCache } from "./api/query-cache.js";

// Poll interval for the top-nav unread-notifications badge (ticket #237), matching
// the cadence the removed sidebar bell used to poll on.
const UNREAD_POLL_INTERVAL_MS = 60000;

// Routes only ever accessible to an isAdmin=true account (story #430 adds the
// company-enrichment screen alongside the existing trigger panel); a non-admin
// hitting either one silently lands on search, mirroring the pre-#430 gate.
const ADMIN_ROUTES = new Set(["admin", "admin-companies"]);

// Static presentation config (the design-tool "tweaks" panel is omitted in the app build).
const APP_CONFIG = {
  accentColor: "#2950E3",
  density: "balanced",
  startLoggedIn: false,
};

// Story #459 / ADR 0027 (social login): the provider's redirect_uri points at this UI's
// /oauth/{provider}/callback route (not at auth-service). App.jsx has no URL router today
// (a plain in-memory `route` restored from sessionStorage), so this is read directly off
// window.location on first render to decide whether we just landed back from a provider.
const OAUTH_CALLBACK_PATH_RE = /^\/oauth\/(google|github)\/callback\/?$/;
// OAUTH-UI-CTX-1/2: loginModalJob/authPromptReason live only in React state, which a
// full-page provider redirect-and-back clears. Persisted here just before the redirect
// and restored once the callback resolves.
const OAUTH_CONTEXT_KEY = "jobhub_oauth_context";

function parseOAuthCallbackProvider() {
  try {
    const m = window.location.pathname.match(OAUTH_CALLBACK_PATH_RE);
    return m ? m[1] : null;
  } catch { return null; }
}

// Drops the /oauth/{provider}/callback path once it's been handled, so a re-render never
// re-mounts OAuthCallbackScreen and replays the (now consumed) code+state.
function clearOAuthCallbackUrl() {
  try { window.history.replaceState({}, "", "/"); } catch {}
}

function App() {
  const t = APP_CONFIG;
  const [authed, setAuthed] = React.useState(t.startLoggedIn);
  const [account, setAccount] = React.useState(null); // the real signed-in account (never a demo identity)
  const [authScreen, setAuthScreen] = React.useState("login"); // login | signup

  // Story #506 / ADR 0028: App owns the single GET /auth/oauth/providers fetch and
  // threads the result down to LoginScreen, SignUpScreen and LoginModal as a prop
  // (mirroring how onLogoClick/onBeforeSocialRedirect are already threaded). One
  // effect, empty deps: it runs once per App mount regardless of how many auth
  // surfaces render or how a signed-out session bounces between them (AVAIL-8,
  // TC-506-D20). Starts hidden (UI-AVAIL-5) and fails open on any rejection or
  // non-200 (UI-AVAIL-6). Wrapped in Promise.resolve().then(...) so even a synchronous
  // throw from getOAuthProviders() (e.g. an unmocked import in a test) is caught below
  // like any other rejection, instead of crashing the render.
  const [oauthAvailability, setOauthAvailability] = React.useState(HIDE_ALL_PROVIDERS);
  React.useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(() => getOAuthProviders())
      .then((data) => {
        if (cancelled) return;
        const map = { google: false, github: false };
        const providers = (data && data.providers) || [];
        providers.forEach((p) => {
          if (p && (p.provider === "google" || p.provider === "github")) {
            map[p.provider] = !!p.available;
          }
        });
        setOauthAvailability(map);
      })
      .catch(() => {
        if (!cancelled) setOauthAvailability(SHOW_ALL_PROVIDERS);
      });
    return () => { cancelled = true; };
  }, []);

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
  // Email to verify after registration — non-null means we're on the verify screen.
  const [pendingVerifyEmail, setPendingVerifyEmail] = React.useState(null);
  // 2FA login challenge — non-null token means we're on the TOTP-entry step.
  const [pending2faToken, setPending2faToken] = React.useState(null);
  // Story #459: non-null provider means we just landed on /oauth/{provider}/callback and
  // must resolve it (or restart) before anything else renders. Computed once from the URL
  // the app booted with; cleared once the callback is handled (success, challenge, or
  // restart) so a later re-render never replays the same code+state.
  const [oauthProvider, setOauthProvider] = React.useState(parseOAuthCallbackProvider);
  // OAUTH-2FA-1/2: when the OAuth callback itself resolves to a 2FA challenge, the
  // loginModalJob/authPromptReason restored from sessionStorage must be carried across
  // the extra TOTP step too — stashed here until handleVerifyTwoFactor completes.
  const [oauthResumeContext, setOauthResumeContext] = React.useState(null);
  const [openAppData, setOpenAppData] = React.useState(null);
  // AC-5 (story #182): set when a deep-linked applicationId (e.g. from a notification)
  // could not be fetched (deleted/inaccessible). Distinguishes the graceful not-found
  // state from the ordinary "still loading" gap where openAppData is briefly null.
  const [openAppNotFound, setOpenAppNotFound] = React.useState(false);
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
  // Story #304: the Settings section to land on when navigating there from the command
  // palette's settings index (e.g. "Notification preferences" -> "notifications").
  // null means "no incoming target" so SettingsScreen keeps its own default.
  const [settingsTargetSection, setSettingsTargetSection] = React.useState(null);
  // Story #460: the apply-profile quick-access drawer. Lifted here (rather than
  // owned by JobSearchScreen or JobDetailDrawer) because it has two trigger
  // points across two different components and must be a single instance so
  // it can be visible alongside the Job Detail drawer (AC-460-2) with its own
  // independent dismissal (BR-9).
  const [applyProfileDrawerOpen, setApplyProfileDrawerOpen] = React.useState(false);
  // #483: transient "closing" flags. When a drawer is dismissed we keep it
  // mounted for one animation cycle with a --closing class so it backtracks out
  // the way it came in, then unmount it. Both drawers share DRAWER_EXIT_MS.
  const [jobDrawerClosing, setJobDrawerClosing] = React.useState(false);
  const [applyDrawerClosing, setApplyDrawerClosing] = React.useState(false);
  const [mobileNavOpen, setMobileNavOpen] = React.useState(false);
  // Bumped whenever the DATA store is re-hydrated from the API, to re-render the
  // screens that read the singleton synchronously.
  const [, setDataVersion] = React.useState(0);
  const bumpData = React.useCallback(() => setDataVersion((v) => v + 1), []);
  const [stats, setStats] = React.useState(null); // ApplicationStatsResponse for the dashboard
  // Unread-notifications count for the top-nav "Notifications" badge (ticket #237):
  // restores what the removed sidebar bell used to show. null = unknown (no
  // successful fetch yet, or logged out) so the Sidebar renders no badge.
  const [unreadCount, setUnreadCount] = React.useState(null);
  // Shared by both "delete an unread row" and "mark an unread row read" on the
  // notifications page (ticket #237): both decrement the badge by exactly one,
  // promptly, instead of waiting for the next ~60s poll tick.
  const decrementUnread = React.useCallback(() => setUnreadCount((c) => Math.max(0, (c || 0) - 1)), []);
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
    // Story #483 (#3): warm the apply-profile cache after sign-in so the first
    // open of the quick-access drawer is instant. Best-effort, never blocks.
    prefetchApplyProfile();
    bumpData();
  }, [bumpData]);

  function clearUserData() {
    DATA.applications.length = 0;
    DATA.saved.length = 0;
    setStats(null);
    setAppliedJobIds(new Set());
    setSavedIds(new Set());
    // Story #329: drop the in-memory job search/facets cache on logout / session boundary.
    // Public job search data isn't user-scoped, so this is defense-in-depth (and
    // future-proofing in case saved jobs/filters are ever routed through the cache).
    clearQueryCache();
    // Story #483 (#3): the apply-profile cache IS user-scoped, so it must be
    // dropped on the session boundary so it can never leak across accounts.
    clearApplyProfileCache();
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
        // Story #329: a forced-logout branch (stale/invalid JWT on boot) also clears the
        // query cache, defense-in-depth alongside the explicit-logout path in clearUserData().
        clearQueryCache();
        if (!cancelled) { setAccount(null); setAuthed(false); }
      } finally {
        if (!cancelled) setBooting(false);
      }
    })();
    return () => { cancelled = true; };
  }, [loadUserData]);

  // Poll the unread-notifications count while authenticated (ticket #237). Mirrors
  // the removed bell's polling: fetch immediately, then every ~60s; a 401 logs the
  // user out instead of leaving a stale badge. Stops entirely while logged out or
  // while the app is still booting/restoring a session.
  React.useEffect(() => {
    if (!USE_API || booting || !authed) return;
    let cancelled = false;

    const poll = async () => {
      try {
        const res = await getUnreadCount();
        if (!cancelled) setUnreadCount(Number(res?.count ?? 0));
      } catch (err) {
        if (cancelled) return;
        if (err && err.status === 401) {
          handleLogout();
          return;
        }
        // transient failure: retain the last known value
      }
    };

    poll();
    const interval = setInterval(poll, UNREAD_POLL_INTERVAL_MS);
    return () => { cancelled = true; clearInterval(interval); };
  }, [authed, booting]);

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
      try {
        const res = await login({ email, password }); // throws ApiError (401) on invalid credentials
        if (res && res.twoFactorRequired) {
          // 2FA account: the first step never returns a usable token. Present the
          // TOTP code-entry step instead of completing the login.
          setPending2faToken(res.twoFactorToken);
          return;
        }
        acc = res?.account || null;
      } catch (ex) {
        if (ex && ex.status === 403) {
          // Account exists but the email was never verified (e.g. the user closed
          // the verify-code screen after registering). Send a fresh code and route
          // to the verify screen instead of surfacing a login error.
          try { await resendVerification(email); } catch {}
          setPendingVerifyEmail(email);
          return;
        }
        throw ex;
      }
    } else {
      if (!email || !password) throw new Error("Enter your email and password.");
      acc = { email };
    }
    completeLogin(acc, "Welcome back.");
  }

  // Second step of a 2FA login: submit the challenge token + TOTP code.
  // Re-throws on failure so TwoFactorLoginStep can show the right message and
  // decide whether to allow a retry (401/429) or force a restart (400 = expired
  // or already-consumed token).
  async function handleVerifyTwoFactor(totpCode) {
    const res = await loginTwoFactor({ twoFactorToken: pending2faToken, totpCode });
    setPending2faToken(null);
    // OAUTH-2FA-1/2/3: when this challenge came from the OAuth callback, resume whatever
    // apply/auth-prompt context was restored from sessionStorage rather than the (empty,
    // post-full-page-reload) React state completeLogin would otherwise read.
    const ctx = oauthResumeContext;
    setOauthResumeContext(null);
    completeLogin(res?.account || null, "Welcome back.", undefined, ctx || undefined);
  }

  function handleRestartLogin() {
    setPending2faToken(null);
    setOauthResumeContext(null);
  }

  // Story #459 / ADR 0027: persists whatever apply/auth-prompt context is currently pending
  // (OAUTH-UI-CTX-1/2) just before the full-page redirect to the provider's consent screen,
  // since that navigation clears all in-memory React state. Passed to the auth screens as
  // onBeforeSocialRedirect; a no-op when nothing is pending (OAUTH-UI-CTX regression guard).
  function handleBeforeSocialRedirect() {
    try {
      sessionStorage.setItem(OAUTH_CONTEXT_KEY, JSON.stringify({
        loginModalJob: loginModalJob || null,
        authPromptReason: authPromptReason || null,
      }));
    } catch {}
  }

  // Reads back (and clears) the context persisted by handleBeforeSocialRedirect. Always
  // returns a stable shape so completeLogin's override path never has to guess.
  function readOAuthPendingContext() {
    try {
      const raw = sessionStorage.getItem(OAUTH_CONTEXT_KEY);
      sessionStorage.removeItem(OAUTH_CONTEXT_KEY);
      const parsed = raw ? JSON.parse(raw) : null;
      return {
        loginModalJob: (parsed && parsed.loginModalJob) || null,
        authPromptReason: (parsed && parsed.authPromptReason) || null,
      };
    } catch {
      return { loginModalJob: null, authPromptReason: null };
    }
  }

  // OAuthCallbackScreen's onComplete: the resolved LoginResponse from
  // POST /oauth/{provider}/callback — either a completed login or a 2FA challenge
  // (OAUTH-2FA-1/2), same shape POST /login already returns.
  function handleOAuthCallbackComplete(res) {
    setOauthProvider(null);
    clearOAuthCallbackUrl();
    const ctx = readOAuthPendingContext();
    if (res && res.twoFactorRequired) {
      setOauthResumeContext(ctx);
      setPending2faToken(res.twoFactorToken);
      return;
    }
    completeLogin(res?.account || null, "Welcome back.", undefined, ctx);
  }

  // OAuthCallbackScreen's onRestart: consent was denied (OAUTH-ERR-1) or the callback
  // failed (OAUTH-ERR-2/3/4, OAUTH-REFUSE-3) and the user chose to go back to sign in.
  function handleOAuthCallbackRestart() {
    setOauthProvider(null);
    clearOAuthCallbackUrl();
    try { sessionStorage.removeItem(OAUTH_CONTEXT_KEY); } catch {}
    setRoute("login");
    setAuthScreen("login");
  }

  async function handleSignup(name, email, password) {
    let acc = null;
    const parts = (name || "").trim().split(/\s+/).filter(Boolean);
    const firstName = parts.shift() || "";
    const lastName = parts.join(" ") || "";
    if (USE_API) {
      const regResponse = await register({ firstName, lastName, email, password });
      // When the backend requires email verification, route to the verify screen
      // instead of auto-logging in. The user must verify before they can sign in.
      if (regResponse && regResponse.verificationRequired) {
        setPendingVerifyEmail(email);
        return;
      }
      // Backend did not require verification (e.g. feature flag off) — log in normally.
      const res = await login({ email, password });
      acc = res?.account || regResponse;
    } else {
      if (!name || !email || !password) throw new Error("Fill in all fields.");
      acc = { firstName, lastName, email };
    }
    // A brand-new account lands on the public job search page.
    completeLogin(acc, "Account created.", "search");
  }

  // overrideContext (story #459): when the login just completed via the OAuth callback
  // (optionally after a 2FA step), loginModalJob/authPromptReason were cleared by the
  // full-page provider redirect, so the resume target must come from what was restored
  // from sessionStorage (handleOAuthCallbackComplete / handleVerifyTwoFactor) instead of
  // this render's (empty) React state. Password login/signup never pass it, so they keep
  // reading the live state exactly as before.
  function completeLogin(acc, toast, forcedRoute, overrideContext) {
    setAccount(acc || null);
    setAuthed(true);
    pushToast(toast || "Welcome back.");
    loadUserData(); // pull the user's real applications + saved jobs
    const job = overrideContext ? overrideContext.loginModalJob : loginModalJob;
    const reason = overrideContext ? overrideContext.authPromptReason : authPromptReason;
    if (job) {
      setLoginModalJob(null);
      setTimeout(() => handleApply(job, true), 300);
      return;
    }
    if (reason) {
      setAuthPromptReason(null);
      setTimeout(() => { setRoute(reason); }, 100);
      return;
    }
    // Default landing (and after signup, and OAUTH-UI-3's no-pending-context case): the
    // public job search page.
    setRoute(forcedRoute || "search");
  }

  function handleLogout() {
    logout(); // drop the JWT
    setAccount(null);
    setAuthed(false);
    clearUserData(); // drop the user's applications/saved from the store
    setApplyProfileDrawerOpen(false); setApplyDrawerClosing(false); // #483: never leave the drawer open across a sign-out
    setJobDrawerClosing(false);
    setRoute("search");
    setOpenAppData(null);
    setOpenAppNotFound(false);
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
    setOpenAppNotFound(false);
    setRoute("applications");
    pushToast(`Deleted application for ${c.name}.`, "trash");
    if (USE_API && app.apiId) {
      deleteApplication(app.apiId).then(() => loadUserData()).catch(() => {});
    }
  }

  function handleOpenApp(app) {
    setOpenAppNotFound(false);
    setOpenAppData(app);   // show immediately (list object has no timeline)
    setRoute("application");
    if (USE_API && app?.apiId) {
      refreshOpenApp(app.apiId); // fetch the detail (with timeline) and swap it in
    }
  }

  // Deep-link entry point (story #182): open an application detail given only its
  // applicationId, with no list-shaped object available yet (e.g. a notification-bell
  // click). Reuses refreshOpenApp's fetch, but distinguishes "fetch failed because the
  // application is gone" (AC-5: graceful not-found state) from the ordinary loading gap.
  async function handleOpenApplicationById(applicationId) {
    if (!applicationId) return;
    setOpenAppNotFound(false);
    setOpenAppData(null);
    setRoute("application");
    if (!USE_API) {
      setOpenAppNotFound(true);
      return;
    }
    try {
      const dto = await getApplication(applicationId);
      setOpenAppData(appFromApi(dto, DATA));
    } catch (e) {
      setOpenAppNotFound(true); // AC-5 / AC-13: deleted or inaccessible application
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

  // Story #304: selecting an entry from the command palette's settings index (BR-7).
  // Admin panel navigates to the top-level admin route (goto() already gates non-admins);
  // the eight Settings-section entries navigate to Settings with their target section
  // active, replacing whatever section was last shown.
  function handleSelectSettings(entry) {
    if (entry.route === "admin") {
      goto("admin");
      return;
    }
    setSettingsTargetSection(entry.section);
    goto("settings");
  }

  // Story #460 / BR-7: "Update in settings" (and the drawer's own all-empty-state
  // CTA) always land on the same single navigation target: Settings -> Apply
  // profile. Reuses the exact mechanism handleSelectSettings uses for the
  // command palette's settings index. Instant close (no exit animation) since
  // the whole screen is being replaced.
  function handleOpenApplyProfileSettings() {
    setApplyProfileDrawerOpen(false);
    setApplyDrawerClosing(false);
    setSettingsTargetSection("apply-profile");
    goto("settings");
  }

  // #483: animated dismissals. Each keeps the drawer mounted for one exit
  // animation (DRAWER_EXIT_MS) before actually unmounting it.
  const DRAWER_EXIT_MS = 240;

  function closeApplyDrawer() {
    if (!applyProfileDrawerOpen || applyDrawerClosing) return;
    setApplyDrawerClosing(true);
    setTimeout(() => { setApplyProfileDrawerOpen(false); setApplyDrawerClosing(false); }, DRAWER_EXIT_MS);
  }

  function toggleApplyDrawer() {
    if (applyProfileDrawerOpen) closeApplyDrawer();
    else { setApplyDrawerClosing(false); setApplyProfileDrawerOpen(true); }
  }

  function closeJobPostOnly() {
    setJobDrawerClosing(true);
    setTimeout(() => { setSelectedJob(null); setJobDrawerClosing(false); }, DRAWER_EXIT_MS);
  }

  // Closing the job post takes the docked apply drawer (which sits behind it)
  // with it, but in sequence (#483): the apply drawer backtracks out FIRST, then
  // once it is gone the job post backtracks out. Not simultaneously.
  function closeJobDrawer() {
    if (jobDrawerClosing) return;
    if (applyProfileDrawerOpen && !applyDrawerClosing) {
      closeApplyDrawer();
      setTimeout(closeJobPostOnly, DRAWER_EXIT_MS); // start the job exit after the apply exit finishes
    } else {
      closeJobPostOnly();
    }
  }

  // #483: background (dark-area) click on the apply drawer. When the apply
  // profile and job post are shown SIDE BY SIDE (wide screen: the apply drawer
  // docks beside the job post, so the dark gutter frames both), a background
  // click dismisses BOTH. When the apply profile sits ON TOP of the job post
  // (narrow screen, below the 1280px dock breakpoint) or there is no job post,
  // it closes only the apply profile, revealing the job post behind. The X /
  // Esc / trigger-toggle always close only the apply profile. Breakpoint kept
  // in sync with the 1280px media query in styles.css (.apply-drawer--docked).
  function handleApplyBackdropClick() {
    const sideBySide = !!selectedJob && typeof window !== "undefined" &&
      window.matchMedia && window.matchMedia("(min-width: 1280px)").matches;
    if (sideBySide) closeJobDrawer();
    else closeApplyDrawer();
  }

  function goto(r) {
    setMobileNavOpen(false);
    if (PROTECTED_ROUTES.has(r) && !authed) {
      setLoginModalJob(null);
      setAuthPromptReason(r);
      return;
    }
    // Admin routes are only accessible when isAdmin=true; non-admins silently land on search.
    if (ADMIN_ROUTES.has(r) && !isAdmin) {
      setRoute("search");
      return;
    }
    setOpenAppData(null);
    setOpenAppNotFound(false);
    setSelectedJob(null);
    setAuthPromptReason(null);
    setRoute(r);
    if (r === "login") {
      setAuthScreen("login");
    }
  }

  // 2FA login gate — shown after step 1 returns a challenge (twoFactorRequired=true),
  // whether that step was password login or the OAuth callback (OAUTH-2FA-1/2). Takes
  // priority over everything, including the OAuth-callback gate below and `booting`: once
  // OAuthCallbackScreen has resolved into a challenge, this is what the user sees next.
  if (pending2faToken) {
    return (
      <TwoFactorLoginStep
        twoFactorToken={pending2faToken}
        onVerify={handleVerifyTwoFactor}
        onRestart={handleRestartLogin}
      />
    );
  }

  // OAuth callback gate (story #459 / ADR 0027) — the browser just landed back on
  // /oauth/{provider}/callback after the provider's consent screen. Resolved before
  // `booting`'s own session-restore attempt so the two never race for the same render.
  if (oauthProvider) {
    return (
      <OAuthCallbackScreen
        provider={oauthProvider}
        onComplete={handleOAuthCallbackComplete}
        onRestart={handleOAuthCallbackRestart}
      />
    );
  }

  if (booting) {
    return <LoadingScreen />;
  }

  // Email verification gate — shown immediately after registration when
  // the backend requires the user to verify their address before logging in.
  if (pendingVerifyEmail) {
    return (
      <VerifyEmailScreen
        email={pendingVerifyEmail}
        onVerified={() => {
          // Verification succeeded — route to login so the user can sign in.
          setPendingVerifyEmail(null);
          setAuthScreen("login");
          pushToast("Email verified! You can now sign in.", "check");
        }}
        onBackToLogin={() => {
          setPendingVerifyEmail(null);
          setAuthScreen("login");
        }}
      />
    );
  }

  // Auth screens (full page). Shown for the login/signup routes and as a hard gate
  // in front of any protected route reached while logged out (e.g. a protected route
  // restored from sessionStorage on reload).
  const PROTECTED_ROUTES = new Set(["dashboard", "applications", "application", "saved", "settings", "admin", "admin-companies", "notifications"]);
  if (!authed && (route === "login" || route === "signup" || PROTECTED_ROUTES.has(route))) {
    if (authScreen === "signup" || route === "signup") {
      return <SignUpScreen onSignUp={handleSignup} onSwitch={() => setAuthScreen("login")} onBeforeSocialRedirect={handleBeforeSocialRedirect} onLogoClick={() => goto("search")} availability={oauthAvailability} />;
    }
    return <LoginScreen onLogin={handleLogin} onSwitch={() => setAuthScreen("signup")} onBeforeSocialRedirect={handleBeforeSocialRedirect} onLogoClick={() => goto("search")} availability={oauthAvailability} />;
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

  const isAdmin = !!(account && account.isAdmin);

  const screenMap = {
    dashboard:    <DashboardScreen goto={goto} openApp={handleOpenApp} openSearch={openSearch} onAddApp={() => setShowAddApp(true)} stats={stats} />,
    search:       <JobSearchScreen goto={goto} onSaveToggle={handleSaveToggle} savedIds={visibleSavedIds} openJob={handleOpenJob} appliedJobIds={visibleAppliedIds} authed={authed} openSearch={openSearch} onOpenApplyProfile={authed ? toggleApplyDrawer : null} />,
    applications: <ApplicationsScreen openApp={handleOpenApp} openSearch={openSearch} onAddApp={() => setShowAddApp(true)} onLogout={handleLogout} />,
    application:  openAppNotFound
      ? <ApplicationNotFoundScreen onBack={() => goto("applications")} />
      : openAppData && <ApplicationDetailScreen app={openAppData} goto={goto} onBack={() => goto("applications")} openSearch={openSearch} onDelete={handleDeleteApp} onStatusChange={persistStatusChange} onNotesSave={persistNotes} onEditSave={persistEdit} onLogout={handleLogout} />,
    saved:        <SavedScreen savedIds={visibleSavedIds} onSaveToggle={handleSaveToggle} openJob={handleOpenJob} goto={goto} appliedJobIds={visibleAppliedIds} openSearch={openSearch} />,
    settings:     <SettingsScreen authed={authed} account={account} onLogout={handleLogout} onLogin={() => { setRoute("login"); setAuthScreen("login"); }} openSearch={openSearch} pushToast={pushToast} initialSection={settingsTargetSection} />,
    notifications: <NotificationsScreen goto={goto} openSearch={openSearch} onOpenApplication={handleOpenApplicationById} onLogout={handleLogout} onAllRead={() => setUnreadCount(0)} onUnreadDeleted={decrementUnread} onUnreadRead={decrementUnread} />,
    // Admin pages: only rendered when account.isAdmin is true; non-admin access falls through to search.
    admin: isAdmin ? <AdminPage account={account} /> : null,
    "admin-companies": isAdmin ? <AdminCompaniesPage account={account} /> : null,
  };

  const sidebarCurrent = route === "application" ? "applications" : route;

  return (
    <MobileNavContext.Provider value={mobileNav}>
    <div className="app">
      <Sidebar current={sidebarCurrent} onNav={goto} appCounts={authed ? appCounts : { total: 0, interview: 0, applied: 0 }} savedCount={authed ? savedIds.size : 0} unreadCount={authed ? unreadCount : null} authed={authed} account={account} mobileOpen={mobileNavOpen} onClose={() => setMobileNavOpen(false)} isAdmin={isAdmin} />
      <main className="main">
        {screenMap[route] || screenMap.search}
      </main>

      {/* Job detail drawer */}
      {selectedJob && (
        <JobDetailDrawer
          job={selectedJob}
          // #483: the apply-profile drawer, when opened from here, docks behind
          // this job post. Closing the job post takes the attached apply drawer
          // with it (both animate out together) so it never orphans.
          onClose={closeJobDrawer}
          closing={jobDrawerClosing}
          onApply={handleApply}
          onSave={handleSaveToggle}
          isSaved={authed && savedIds.has(selectedJob.id)}
          isApplied={authed && appliedJobIds.has(selectedJob.id)}
          authed={authed}
          onOpenApplyProfile={authed ? toggleApplyDrawer : null}
        />
      )}

      {/* Apply profile quick-access drawer (story #460): a single App-level
          instance so it can be visible alongside the Job Detail drawer above,
          each with its own independent dismissal. */}
      {applyProfileDrawerOpen && (
        <ApplyProfileDrawer
          authed={authed}
          docked={!!selectedJob}
          closing={applyDrawerClosing}
          pushToast={pushToast}
          onClose={closeApplyDrawer}
          onBackdropClick={handleApplyBackdropClick}
          onUpdateInSettings={handleOpenApplyProfileSettings}
          onLogout={handleLogout}
          onLogin={() => { setApplyProfileDrawerOpen(false); setApplyDrawerClosing(false); setRoute("login"); setAuthScreen("login"); }}
        />
      )}

      {/* Login modal (triggered when applying without auth or accessing protected pages) */}
      {loginModalJob && (
        <LoginModal
          onClose={() => setLoginModalJob(null)}
          onLogin={handleLogin}
          onSignUp={handleSignup}
          jobTitle={`${loginModalJob.title} at ${DATA.coOf(loginModalJob.co).name}`}
          onBeforeSocialRedirect={handleBeforeSocialRedirect}
          availability={oauthAvailability}
        />
      )}
      {authPromptReason && !loginModalJob && (
        <LoginModal
          onClose={() => setAuthPromptReason(null)}
          onLogin={handleLogin}
          onSignUp={handleSignup}
          reason={authPromptReason}
          onBeforeSocialRedirect={handleBeforeSocialRedirect}
          availability={oauthAvailability}
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
          onSelectSettings={handleSelectSettings}
          isAdmin={isAdmin}
        />
      )}
    </div>
    </MobileNavContext.Provider>
  );
}

// Animated boot loader: a commuter walks into the building, reappears on the
// other side, and walks back in — looping while the app hydrates.
// AC-5 / AC-13 (story #182): graceful not-found state for an application detail that
// could not be fetched (deleted/inaccessible, or a stale list referencing a since-deleted
// application). Reused as-is for the navigate-from-notification deep link (TC-F-40..42);
// no separate empty/error state is introduced for the stale-list variant.
function ApplicationNotFoundScreen({ onBack }) {
  return (
    <div className="content" data-testid="application-not-found">
      <Empty
        icon="alert-circle"
        title="This application could not be found"
        desc="It may have been deleted, or you may no longer have access to it."
        cta={<Button variant="primary" icon="arrow-left" onClick={onBack}>Back to applications</Button>}
      />
    </div>
  );
}

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
