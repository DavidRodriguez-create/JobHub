import React from "react";
import Icon from "../Icon.jsx";
import * as UI from "../ui.jsx";
import { isApplyProfileEmpty } from "./applyProfileFields.js";
import { useApplyProfileCopy } from "./useApplyProfileCopy.js";
import { ApplyProfileReadOnlyList } from "./ApplyProfileReadOnlyList.jsx";
import { getCachedApplyProfile, fetchApplyProfile } from "./applyProfileCache.js";

const { Button } = UI;

/* ── Apply profile quick-access drawer ──
   Story #460 / architect design note #478. A LEFT slide-out, read-only view of
   the same personal answer bank as Settings -> Apply profile
   (screens/SavedSettings.jsx's ApplyProfileSection), reachable from the Job
   Search screen and the Job Detail drawer so a user can copy an answer
   without leaving the browsing/applying context.

   Read-only guarantee (BR-1/BR-8/AC-460-21): this component never imports or
   calls saveApplyProfile, and renders no editable input/toggle/add-remove
   control anywhere, only static text plus copy buttons plus navigation.

   User-scoped, not job-scoped (BR-5): it takes no job prop and reads no job
   context; the fetched profile is identical regardless of which job (if any)
   is open in the Job Detail drawer. */
export function ApplyProfileDrawer({ authed, docked, closing, pushToast, onClose, onBackdropClick, onUpdateInSettings, onLogout, onLogin }) {
  // Story #483 (#3): seed from the module cache so a reopen renders the
  // last-known values instantly (no spinner). Null seed -> the loading state
  // shows until the first fetch settles.
  const [profile, setProfile] = React.useState(() => getCachedApplyProfile());
  const [loadError, setLoadError] = React.useState(false);
  const { copiedField, handleCopy } = useApplyProfileCopy(pushToast);

  // Stale-while-revalidate: still fetch on every open (freshness, §4.10 /
  // AC-460-22 preserved), but when the cache already holds a value the fetch is
  // a silent background revalidation over the instantly-rendered cached data
  // rather than a spinner. BR-8: no GET is attempted while signed out.
  React.useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    const hadCache = getCachedApplyProfile() != null;
    if (!hadCache) {
      setLoadError(false);
      setProfile(null);
    }
    fetchApplyProfile()
      .then((data) => {
        if (cancelled) return;
        setProfile(data);
        setLoadError(false);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err && err.status === 401) {
          // Mirrors ApplyProfileSection's existing 401 handling: sign the user
          // out. The drawer also closes itself so it never lingers open
          // showing an error banner after the app returns to signed-out
          // (AC-460-16): it does not rely on the parent unmounting it.
          if (onLogout) onLogout();
          if (onClose) onClose();
          return;
        }
        // Only surface an error when there is nothing cached to show; a failed
        // background revalidation leaves the last-known values on screen.
        if (!hadCache) setLoadError(true);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authed]);

  // Esc closes only THIS drawer, even when the Job Detail drawer is also open
  // and owns its own window "keydown" Escape listener (AC-460-5 / BR-9). A
  // capture-phase listener on window always runs before any bubble-phase
  // window listener (window is visited first on the way down, last on the
  // way up), regardless of which drawer mounted first, so calling
  // stopPropagation() here halts the event before JobDetailDrawer's own
  // (bubble-phase) handler ever runs, no matter the mount order.
  React.useEffect(() => {
    const handler = (e) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
    };
    window.addEventListener("keydown", handler, true);
    return () => window.removeEventListener("keydown", handler, true);
  }, [onClose]);

  return (
    <>
      {/* Backdrop uses onBackdropClick (#483): side by side with the job post it
          dismisses both; stacked on top of it, only the apply drawer. The X /
          Esc always close just this drawer (onClose). */}
      <div className={"apply-drawer-backdrop" + (docked ? " apply-drawer-backdrop--docked" : "") + (closing ? " apply-drawer-backdrop--closing" : "")} onClick={onBackdropClick || onClose} />
      <div className={"apply-drawer" + (docked ? " apply-drawer--docked" : "") + (closing ? " apply-drawer--closing" : "")} role="dialog" aria-label="Apply profile">
        <div className="apply-drawer-head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>Apply profile</div>
            <div style={{ fontSize: 12, color: "var(--color-ink-3)", marginTop: 2 }}>
              Answers you reuse on every external application, ready to copy.
            </div>
          </div>
          <Button variant="ghost" size="sm" icon="x" onClick={onClose} aria-label="Close" />
        </div>

        <div className="apply-drawer-body">
          {!authed ? (
            <div data-testid="apply-profile-unauth" style={{ textAlign: "center", padding: "24px 0" }}>
              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Not signed in</div>
              <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginBottom: 16 }}>
                Sign in to fill in and copy your apply profile.
              </div>
              <Button variant="primary" onClick={onLogin}>Sign in</Button>
            </div>
          ) : loadError ? (
            <div data-testid="apply-profile-error" className="banner-warning" role="alert">
              <Icon name="info" size={14} style={{ marginRight: 6, verticalAlign: "-2px" }} />
              Couldn't load your apply profile. Please try again later.
            </div>
          ) : !profile ? (
            <div data-testid="apply-profile-loading" style={{ padding: "24px 0", color: "var(--color-ink-3)", fontSize: 13 }}>
              Loading…
            </div>
          ) : isApplyProfileEmpty(profile) ? (
            <div data-testid="apply-profile-empty" style={{ textAlign: "center", padding: "24px 0" }}>
              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>You haven't filled in your apply profile yet</div>
              <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginBottom: 16 }}>
                Fill it in once in Settings, then copy any answer from here while you apply.
              </div>
              <Button variant="primary" icon="settings" onClick={onUpdateInSettings} data-testid="apply-profile-empty-cta">
                Fill in your apply profile
              </Button>
            </div>
          ) : (
            <ApplyProfileReadOnlyList profile={profile} copiedField={copiedField} onCopy={handleCopy} />
          )}
        </div>

        {authed && (
          <div className="apply-drawer-foot">
            <Button variant="primary" icon="settings" onClick={onUpdateInSettings} data-testid="apply-profile-update-settings">
              Update in settings
            </Button>
          </div>
        )}
      </div>
    </>
  );
}
