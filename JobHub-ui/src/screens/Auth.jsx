import React from "react";
import Icon from "../components/Icon.jsx";
// JobHub — Login / Signup screens + Login modal + Email Verification screen
import { Button, Input, Field, Modal } from "../components/ui.jsx";
import { verifyEmail, resendVerification, startOAuth, completeOAuthLogin } from "../api/auth.js";

// Story #506 / ADR 0028: provider availability gates the social buttons on all three
// surfaces (LoginScreen, SignUpScreen, LoginModal). App.jsx is the single owner of the
// GET /auth/oauth/providers fetch (one effect, fired once per app session — AVAIL-8,
// TC-506-D20) and threads the resolved `availability` map down to each surface as a
// prop, the same way it already threads onLogoClick/onBeforeSocialRedirect. These
// screens stay presentational: they render whatever availability they're given and
// carry no fetch/cache logic of their own.
//
// UI-AVAIL-5: the default (no availability passed yet) renders as if neither provider
// were available, so a button never flashes in and then disappears once the real
// result arrives.
const HIDE_ALL_PROVIDERS = Object.freeze({ google: false, github: false });
// UI-AVAIL-6: on any error, timeout, or non-200 response, App.jsx fails open by passing
// this down instead — a broken availability check must never remove an option a
// signed-out user would otherwise have had.
const SHOW_ALL_PROVIDERS = Object.freeze({ google: true, github: true });

// Story #459 / ADR 0027: social sign-in (Google/GitHub) via the authorization-code flow.
// The UI calls GET /oauth/{provider}/start and does a full-page redirect to the returned
// authorizationUrl; the provider redirects back to this UI's /oauth/{provider}/callback
// route with ?code&state (or ?error on a declined consent).
function providerLabel(provider) {
  return provider === "github" ? "GitHub" : "Google";
}

// Classifies a completeOAuthLogin() rejection into the distinct copy each OAUTH-ERR-*/
// OAUTH-REFUSE-3 scenario calls for. The 401 status is shared by two different causes
// (provider-auth-failure vs. unverified-collision refusal, see QAE note 0.6); we tell them
// apart via the ErrorResponse "error" title the backend returns.
function classifyOAuthError(ex, provider) {
  const label = providerLabel(provider);
  const status = ex && ex.status;
  const errorTitle = (ex && ex.body && ex.body.error) || "";
  if (status === 401 && /link|refus/i.test(errorTitle)) {
    return {
      kind: "refused",
      message: `We couldn't automatically link that ${label} account, for your account's safety. Please sign in with your existing email and password instead.`,
    };
  }
  if (status === 401) {
    return { kind: "auth-failed", message: `We couldn't sign you in with ${label}. Please try again.` };
  }
  if (status === 400) {
    return { kind: "state-invalid", message: "Your sign-in session expired or is invalid. Please try again." };
  }
  if (status === 502) {
    return { kind: "provider-outage", message: `${label} is unavailable right now. Try again, or use email/password.` };
  }
  return { kind: "generic", message: (ex && ex.message) || "Something went wrong. Please try again." };
}

function errMessage(ex) {
  if (!ex) return "Something went wrong. Please try again.";
  if (ex.status === 401) return "Incorrect email or password.";
  if (ex.status === 403) return "Your email isn't verified yet. Check your inbox for a verification code, or request a new one.";
  if (ex.status === 409) return "That email is already registered.";
  if (ex.status === 0) return "Can't reach the server. Is the backend running?";
  return ex.message || "Something went wrong. Please try again.";
}

function FormError({ children }) {
  if (!children) return null;
  return (
    <div role="alert" style={{
      fontSize: 13, color: "#b42318", background: "#fef3f2", border: "1px solid #fecdca",
      borderRadius: 8, padding: "8px 12px",
    }}>{children}</div>
  );
}

/* ─── Full-page Login Screen ─── */
function LoginScreen({ onLogin, onSwitch, onBeforeSocialRedirect, onLogoClick, availability = HIDE_ALL_PROVIDERS }) {
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [showPw, setShowPw] = React.useState(false);
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const showSocial = availability.google || availability.github;

  const canSubmit = email.trim() && password && !busy;

  async function submit(e) {
    e.preventDefault();
    if (!canSubmit) return;
    setErr("");
    setBusy(true);
    try {
      await onLogin(email.trim(), password);
    } catch (ex) {
      setErr(errMessage(ex));
      setBusy(false);
    }
  }

  async function handleSocial(provider) {
    setErr("");
    try {
      if (onBeforeSocialRedirect) onBeforeSocialRedirect(provider);
      const { authorizationUrl } = await startOAuth(provider);
      window.location.href = authorizationUrl;
    } catch (ex) {
      setErr(errMessage(ex));
    }
  }

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <button
          type="button"
          onClick={() => { if (typeof onLogoClick === "function") onLogoClick(); }}
          aria-label="JobHub home"
          style={{
            display: "flex", alignItems: "center", gap: 10,
            background: "none", border: "none", padding: 0, margin: 0,
            cursor: "pointer", font: "inherit", textAlign: "left",
          }}
        >
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </button>

        <form className="login-form-card" onSubmit={submit}>
          <div>
            <div className="login-title">Welcome back.</div>
            <div className="login-sub">Sign in to pick up where you left off.</div>
          </div>

          <Field label="Email">
            <Input type="email" placeholder="you@email.com" value={email} onChange={(e) => setEmail(e.target.value)} className="lg" autoFocus />
          </Field>

          <Field label="Password">
            <div style={{ position: "relative" }}>
              <Input type={showPw ? "text" : "password"} placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} className="lg" />
              <button type="button" onClick={() => setShowPw(!showPw)}
                style={{ position: "absolute", right: 10, top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", padding: 4, color: "var(--color-ink-3)" }}>
                <Icon name={showPw ? "eye-off" : "eye"} size={16} />
              </button>
            </div>
          </Field>

          <FormError>{err}</FormError>

          <Button type="submit" variant="primary" size="lg" className="block" disabled={!canSubmit}>
            {busy ? "Signing in…" : "Sign in"}
          </Button>

          {showSocial && <div className="login-or">or</div>}

          {showSocial && (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {availability.google && (
                <Button type="button" variant="secondary" size="lg" className="block" icon="google" onClick={() => handleSocial("google")}>Continue with Google</Button>
              )}
              {availability.github && (
                <Button type="button" variant="secondary" size="lg" className="block" icon="github" onClick={() => handleSocial("github")}>Continue with GitHub</Button>
              )}
            </div>
          )}

          <div style={{ fontSize: 13, color: "var(--color-ink-3)", textAlign: "center" }}>
            New here? <a onClick={onSwitch} style={{ cursor: "pointer" }}>Create an account</a>
          </div>
        </form>

        <div style={{ fontSize: 11, color: "var(--color-ink-4)", textAlign: "center" }}>
          By signing in, you agree to the terms &amp; privacy notice.
        </div>
      </div>

      <div className="login-aside">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="22" height="22" alt="" style={{ filter: "brightness(0) invert(1)", opacity: 0.85 }} />
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "rgba(255,255,255,0.6)", textTransform: "uppercase", letterSpacing: "0.08em" }}>v2026.05</span>
        </div>
        <div>
          <div className="aside-quote">"I had 32 tabs open and a spreadsheet I lost twice. Now I just open JobHub."</div>
          <div className="aside-attribution">— ALEX R., DESIGNER · USES JOBHUB</div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16, paddingTop: 24, borderTop: "1px solid rgba(255,255,255,0.12)" }}>
          {[["12k+", "jobs indexed weekly"], ["Zero", "sponsored posts"], ["100%", "free to use"]].map(([n, l], i) => (
            <div key={i}>
              <div style={{ fontSize: 22, fontWeight: 600, letterSpacing: "-0.022em" }}>{n}</div>
              <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", textTransform: "uppercase", letterSpacing: "0.06em", fontWeight: 500, marginTop: 4 }}>{l}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ─── Full-page Sign Up Screen ─── */
function SignUpScreen({ onSignUp, onSwitch, onBeforeSocialRedirect, onLogoClick, availability = HIDE_ALL_PROVIDERS }) {
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const showSocial = availability.google || availability.github;

  const canSubmit = name.trim() && email.trim() && password.length >= 8 && !busy;

  async function submit(e) {
    e.preventDefault();
    if (!name.trim() || !email.trim() || !password) {
      setErr("Fill in your name, email and password.");
      return;
    }
    if (password.length < 8) {
      setErr("Password must be at least 8 characters.");
      return;
    }
    setErr("");
    setBusy(true);
    try {
      await onSignUp(name.trim(), email.trim(), password);
    } catch (ex) {
      setErr(errMessage(ex));
      setBusy(false);
    }
  }

  async function handleSocial(provider) {
    setErr("");
    try {
      if (onBeforeSocialRedirect) onBeforeSocialRedirect(provider);
      const { authorizationUrl } = await startOAuth(provider);
      window.location.href = authorizationUrl;
    } catch (ex) {
      setErr(errMessage(ex));
    }
  }

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <button
          type="button"
          onClick={() => { if (typeof onLogoClick === "function") onLogoClick(); }}
          aria-label="JobHub home"
          style={{
            display: "flex", alignItems: "center", gap: 10,
            background: "none", border: "none", padding: 0, margin: 0,
            cursor: "pointer", font: "inherit", textAlign: "left",
          }}
        >
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </button>

        <form className="login-form-card" onSubmit={submit}>
          <div>
            <div className="login-title">Create your account.</div>
            <div className="login-sub">Start tracking applications in under a minute.</div>
          </div>

          <Field label="Full name">
            <Input type="text" placeholder="Jordan Lee" value={name} onChange={(e) => setName(e.target.value)} className="lg" autoFocus />
          </Field>

          <Field label="Email">
            <Input type="email" placeholder="you@email.com" value={email} onChange={(e) => setEmail(e.target.value)} className="lg" />
          </Field>

          <Field label="Password">
            <Input type="password" placeholder="At least 8 characters" value={password} onChange={(e) => setPassword(e.target.value)} className="lg" />
          </Field>

          <FormError>{err}</FormError>

          <Button type="submit" variant="primary" size="lg" className="block" disabled={!canSubmit}>
            {busy ? "Creating account…" : "Create account"}
          </Button>

          {showSocial && <div className="login-or">or</div>}

          {showSocial && (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {availability.google && (
                <Button type="button" variant="secondary" size="lg" className="block" icon="google" onClick={() => handleSocial("google")}>Continue with Google</Button>
              )}
              {availability.github && (
                <Button type="button" variant="secondary" size="lg" className="block" icon="github" onClick={() => handleSocial("github")}>Continue with GitHub</Button>
              )}
            </div>
          )}

          <div style={{ fontSize: 13, color: "var(--color-ink-3)", textAlign: "center" }}>
            Already have an account? <a onClick={onSwitch} style={{ cursor: "pointer" }}>Sign in</a>
          </div>
        </form>

        <div style={{ fontSize: 11, color: "var(--color-ink-4)", textAlign: "center" }}>
          By creating an account, you agree to the terms &amp; privacy notice.
        </div>
      </div>

      <div className="login-aside">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="22" height="22" alt="" style={{ filter: "brightness(0) invert(1)", opacity: 0.85 }} />
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "rgba(255,255,255,0.6)", textTransform: "uppercase", letterSpacing: "0.08em" }}>v2026.05</span>
        </div>
        <div>
          <div className="aside-quote">Track applications. Skip the noise.</div>
          <div className="aside-attribution" style={{ marginTop: 16 }}>Every job post, every application, in one place.</div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16, paddingTop: 24, borderTop: "1px solid rgba(255,255,255,0.12)" }}>
          {[["Free", "no credit card"], ["5s", "to apply to a job"], ["100%", "your data"]].map(([n, l], i) => (
            <div key={i}>
              <div style={{ fontSize: 22, fontWeight: 600, letterSpacing: "-0.022em" }}>{n}</div>
              <div style={{ fontSize: 11, color: "rgba(255,255,255,0.55)", textTransform: "uppercase", letterSpacing: "0.06em", fontWeight: 500, marginTop: 4 }}>{l}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ─── Login Modal (popup when trying to apply / open a protected page while not logged in) ─── */
function LoginModal({ onClose, onLogin, onSignUp, jobTitle, reason, onBeforeSocialRedirect, availability = HIDE_ALL_PROVIDERS }) {
  const [mode, setMode] = React.useState("login"); // login or signup
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [name, setName] = React.useState("");
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const showSocial = availability.google || availability.github;

  const reasonLabels = {
    applications: "View your applications",
    saved: "Access your saved jobs",
    dashboard: "See your dashboard",
    settings: "Access your settings",
  };
  const contextLabel = jobTitle || (reason && reasonLabels[reason]) || null;

  const canSubmit = email.trim() && password && (mode === "login" || name.trim()) && !busy;

  async function submit() {
    if (!canSubmit) return;
    setErr("");
    setBusy(true);
    try {
      if (mode === "login") {
        await onLogin(email.trim(), password);
      } else {
        await onSignUp(name.trim(), email.trim(), password);
      }
    } catch (ex) {
      setErr(errMessage(ex));
      setBusy(false);
    }
  }

  async function handleSocial(provider) {
    setErr("");
    try {
      if (onBeforeSocialRedirect) onBeforeSocialRedirect(provider);
      const { authorizationUrl } = await startOAuth(provider);
      window.location.href = authorizationUrl;
    } catch (ex) {
      setErr(errMessage(ex));
    }
  }

  return (
    <Modal title={mode === "login" ? "Sign in to continue" : "Create account to continue"} onClose={onClose}
      footer={
        <div style={{ display: "flex", width: "100%", justifyContent: "space-between", alignItems: "center" }}>
          <span style={{ fontSize: 13, color: "var(--color-ink-3)" }}>
            {mode === "login" ? (
              <>New here? <a onClick={() => { setMode("signup"); setErr(""); }} style={{ cursor: "pointer" }}>Create an account</a></>
            ) : (
              <>Have an account? <a onClick={() => { setMode("login"); setErr(""); }} style={{ cursor: "pointer" }}>Sign in</a></>
            )}
          </span>
          <Button variant="primary" onClick={submit} disabled={!canSubmit}>
            {busy ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
          </Button>
        </div>
      }>
      <form className="login-modal-body" onSubmit={(e) => { e.preventDefault(); submit(); }}>
        {contextLabel && (
          <div style={{ padding: "10px 14px", background: "var(--color-brand-50)", border: "1px solid var(--color-brand-200)", borderRadius: 8, fontSize: 13 }}>
            <span style={{ color: "var(--color-brand-700)", fontWeight: 500 }}>{jobTitle ? "Applying to:" : "To continue:"}</span>
            <span style={{ color: "var(--color-ink)", marginLeft: 6 }}>{contextLabel}</span>
          </div>
        )}

        {mode === "signup" && (
          <Field label="Full name">
            <Input type="text" placeholder="Jordan Lee" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
        )}

        <Field label="Email">
          <Input type="email" placeholder="you@email.com" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
        </Field>

        <Field label="Password">
          <Input type="password" placeholder={mode === "login" ? "••••••••" : "At least 8 characters"} value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>

        <FormError>{err}</FormError>

        {/* hidden submit makes Enter submit the form */}
        <button type="submit" style={{ display: "none" }} aria-hidden="true" tabIndex={-1} />

        {showSocial && <div className="login-or">or</div>}

        {showSocial && (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {availability.google && (
              <Button type="button" variant="secondary" className="block" icon="google" onClick={() => handleSocial("google")}>Continue with Google</Button>
            )}
            {availability.github && (
              <Button type="button" variant="secondary" className="block" icon="github" onClick={() => handleSocial("github")}>Continue with GitHub</Button>
            )}
          </div>
        )}
      </form>
    </Modal>
  );
}

/* ─── Email Verification Screen ─── */
// Shown after registration when verificationRequired=true.
// Accepts: email (pre-filled from registration), onVerified (callback on success),
// onBackToLogin (callback to return to login screen).
function VerifyEmailScreen({ email, onVerified, onBackToLogin }) {
  const [code, setCode] = React.useState("");
  const [err, setErr] = React.useState("");
  const [resendMsg, setResendMsg] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [resendBusy, setResendBusy] = React.useState(false);

  const canSubmit = code.trim().length === 6 && !busy;

  async function handleSubmit(e) {
    e.preventDefault();
    if (!canSubmit) return;
    setErr("");
    setBusy(true);
    try {
      await verifyEmail({ email, code: code.trim() });
      onVerified();
    } catch (ex) {
      if (ex && ex.status === 400) {
        setErr("That code is invalid or has expired. Please check your email or request a new code.");
      } else {
        setErr(ex && ex.message ? ex.message : "Something went wrong. Please try again.");
      }
      setBusy(false);
    }
  }

  async function handleResend() {
    if (resendBusy) return;
    setResendMsg("");
    setErr("");
    setResendBusy(true);
    try {
      await resendVerification(email);
      setResendMsg("Email sent! Check your inbox for a new code.");
    } catch (ex) {
      if (ex && ex.status === 429) {
        setResendMsg("Too many requests — try again later.");
      } else {
        setResendMsg("Couldn't send a new code. Please try again.");
      }
    } finally {
      setResendBusy(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </div>

        <div
          className="login-form-card"
          data-testid="verify-email-screen"
        >
          <div>
            <div className="login-title">Verify your email.</div>
            <div className="login-sub">
              We sent a 6-digit code to <strong>{email}</strong>. Enter it below to activate your account.
            </div>
          </div>

          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <Field label="Verification code">
              <Input
                type="text"
                inputMode="numeric"
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                className="lg"
                autoFocus
                maxLength={6}
              />
            </Field>

            {err && (
              <div role="alert" style={{
                fontSize: 13, color: "#b42318", background: "#fef3f2", border: "1px solid #fecdca",
                borderRadius: 8, padding: "8px 12px",
              }}>{err}</div>
            )}

            <Button type="submit" variant="primary" size="lg" className="block" disabled={!canSubmit}>
              {busy ? "Verifying…" : "Verify email"}
            </Button>
          </form>

          <div style={{ fontSize: 13, color: "var(--color-ink-3)", textAlign: "center", marginTop: 8 }}>
            {resendMsg ? (
              <span style={{ color: resendMsg.includes("try again later") ? "#b42318" : "var(--color-ink-2)" }}>
                {resendMsg}
              </span>
            ) : (
              <>
                Didn't receive it?{" "}
                <a
                  onClick={handleResend}
                  role="button"
                  style={{ cursor: resendBusy ? "default" : "pointer", opacity: resendBusy ? 0.5 : 1 }}
                >
                  Resend code
                </a>
              </>
            )}
          </div>

          {onBackToLogin && (
            <div style={{ fontSize: 13, color: "var(--color-ink-3)", textAlign: "center" }}>
              <a onClick={onBackToLogin} style={{ cursor: "pointer" }}>Back to sign in</a>
            </div>
          )}
        </div>

        <div style={{ fontSize: 11, color: "var(--color-ink-4)", textAlign: "center" }}>
          By creating an account, you agree to the terms &amp; privacy notice.
        </div>
      </div>

      <div className="login-aside">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="22" height="22" alt="" style={{ filter: "brightness(0) invert(1)", opacity: 0.85 }} />
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "rgba(255,255,255,0.6)", textTransform: "uppercase", letterSpacing: "0.08em" }}>v2026.05</span>
        </div>
        <div>
          <div className="aside-quote">One last step.</div>
          <div className="aside-attribution" style={{ marginTop: 16 }}>Verify your email to start tracking your job search.</div>
        </div>
      </div>
    </div>
  );
}

/* ─── Two-factor login step ─── */
// Shown after POST /auth/login returns a 2FA challenge (twoFactorRequired=true).
// Accepts: twoFactorToken (the opaque challenge), onVerify (calls loginTwoFactor and
// completes the login on success), onRestart (clears the challenge, returns to login).
function TwoFactorLoginStep({ twoFactorToken, onVerify, onRestart }) {
  const [code, setCode] = React.useState("");
  const [err, setErr] = React.useState("");
  const [expired, setExpired] = React.useState(false);
  const [busy, setBusy] = React.useState(false);

  const canSubmit = code.trim().length > 0 && !busy;

  async function handleSubmit(e) {
    e.preventDefault();
    if (!canSubmit) return;
    setErr("");
    setBusy(true);
    try {
      await onVerify(code.trim());
      // onVerify completes the login (sets state in the parent); nothing else to do.
    } catch (ex) {
      if (ex && ex.status === 400) {
        setExpired(true);
        setErr("Session expired. Please sign in again.");
      } else if (ex && (ex.status === 401 || ex.status === 429)) {
        setErr("Invalid code. Please try again.");
        setCode("");
      } else {
        setErr(ex && ex.message ? ex.message : "Something went wrong. Please try again.");
      }
      setBusy(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </div>

        <div className="login-form-card" data-testid="two-factor-login-step">
          <div>
            <div className="login-title">Enter your authenticator code.</div>
            <div className="login-sub">
              Enter the 6-digit code from your authenticator app, or a backup code.
            </div>
          </div>

          {expired ? (
            <>
              <FormError>{err}</FormError>
              <Button type="button" variant="primary" size="lg" className="block" onClick={onRestart}>
                Sign in again
              </Button>
            </>
          ) : (
            <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <Field label="Authentication code">
                <Input
                  type="text"
                  inputMode="text"
                  placeholder="123456"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  className="lg"
                  autoFocus
                  maxLength={8}
                  aria-label="Authentication code"
                />
              </Field>

              <FormError>{err}</FormError>

              <Button type="submit" variant="primary" size="lg" className="block" disabled={!canSubmit}>
                {busy ? "Verifying…" : "Verify"}
              </Button>
            </form>
          )}

          <div style={{ fontSize: 13, color: "var(--color-ink-3)", textAlign: "center" }}>
            <a onClick={onRestart} style={{ cursor: "pointer" }}>Back to sign in</a>
          </div>
        </div>
      </div>

      <div className="login-aside">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="22" height="22" alt="" style={{ filter: "brightness(0) invert(1)", opacity: 0.85 }} />
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "rgba(255,255,255,0.6)", textTransform: "uppercase", letterSpacing: "0.08em" }}>v2026.05</span>
        </div>
        <div>
          <div className="aside-quote">One more step to keep your account secure.</div>
        </div>
      </div>
    </div>
  );
}

// Story #522 (ADR 0027 follow-up): App.jsx mounts under React.StrictMode, which double
// invokes this effect on mount in dev — a `useRef` guard does not survive that (StrictMode
// discards refs across its dev-only mount -> cleanup -> remount too), so the dedupe has to
// live outside component state. This module-scope map shares one in-flight/settled exchange
// promise across every effect run for the same provider+code+state triple, so the
// single-use authorization code is POSTed at most once, while a genuinely different
// code+state (a fresh sign-in attempt) always starts its own exchange.
//
// Cleanup is deferred by one microtask rather than run inline: StrictMode's dev-only
// mount -> cleanup -> remount for a given component instance happens synchronously in the
// same commit, so the remount's claim below always lands before this microtask fires and
// cancels the release. A later, unrelated remount for the same key only reuses the entry if
// it claims within that same synchronous window; otherwise the entry is already gone and it
// starts a fresh exchange, exactly once.
const oauthExchangeEntries = new Map();

function claimOAuthExchange(key, start) {
  let entry = oauthExchangeEntries.get(key);
  if (!entry) {
    entry = { promise: start(), refCount: 0 };
    oauthExchangeEntries.set(key, entry);
  }
  entry.refCount += 1;
  return entry;
}

function releaseOAuthExchange(key, entry) {
  entry.refCount -= 1;
  Promise.resolve().then(() => {
    if (entry.refCount <= 0 && oauthExchangeEntries.get(key) === entry) {
      oauthExchangeEntries.delete(key);
    }
  });
}

/* ─── OAuth callback screen (story #459 / ADR 0027) ─── */
// Rendered by App.jsx when the browser lands back on the UI's /oauth/{provider}/callback
// route after the provider's consent screen. Reads ?code&state (or ?error) from the URL,
// relays code+state to POST /oauth/{provider}/callback, and reports the outcome upward:
//   - onComplete(loginResponse): a resolved LoginResponse, completed login OR a 2FA
//     challenge (twoFactorRequired + twoFactorToken) — App.jsx decides which.
//   - onRestart(): the user cancelled, or the callback failed and chose to go back.
function OAuthCallbackScreen({ provider, onComplete, onRestart }) {
  const [state, setState] = React.useState({ status: "loading", message: "" });

  React.useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get("error");

    // OAUTH-ERR-1: the provider redirected back with an error indicator instead of a code
    // (consent denied/cancelled) — never contact auth-service in this case.
    if (errorParam) {
      setState({ status: "cancelled", message: "Sign-in was cancelled." });
      return undefined;
    }

    const code = params.get("code");
    const stateParam = params.get("state");
    const key = `${provider}:${code}:${stateParam}`;
    const entry = claimOAuthExchange(key, () => completeOAuthLogin({ provider, code, state: stateParam }));

    entry.promise.then(
      (res) => { if (!cancelled) onComplete(res); },
      (ex) => {
        if (cancelled) return;
        const { message } = classifyOAuthError(ex, provider);
        setState({ status: "error", message });
      },
    );

    return () => { cancelled = true; releaseOAuthExchange(key, entry); };
  }, [provider]);

  if (state.status === "loading") {
    return (
      <div className="login-shell">
        <div className="login-form-side">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
            <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
          </div>
          <div className="login-form-card" data-testid="oauth-callback-loading">
            <div className="login-title">Signing you in…</div>
            <div className="login-sub">Completing sign-in with {providerLabel(provider)}.</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </div>
        <div className="login-form-card" data-testid="oauth-callback-error">
          <div>
            <div className="login-title">
              {state.status === "cancelled" ? "Sign-in cancelled" : "Sign-in didn't complete"}
            </div>
          </div>
          <FormError>{state.message}</FormError>
          <Button type="button" variant="primary" size="lg" className="block" onClick={onRestart}>
            Back to sign in
          </Button>
        </div>
      </div>
    </div>
  );
}

export {
  LoginScreen, SignUpScreen, LoginModal, VerifyEmailScreen, TwoFactorLoginStep, OAuthCallbackScreen,
  HIDE_ALL_PROVIDERS, SHOW_ALL_PROVIDERS,
};
