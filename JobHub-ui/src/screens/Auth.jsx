import React from "react";
import Icon from "../components/Icon.jsx";
// JobHub — Login / Signup screens + Login modal
import { Button, Input, Field, Modal } from "../components/ui.jsx";

const SOCIAL_MSG = "Social sign-in isn't available yet — use your email and password.";

function errMessage(ex) {
  if (!ex) return "Something went wrong. Please try again.";
  if (ex.status === 401) return "Incorrect email or password.";
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
function LoginScreen({ onLogin, onSwitch }) {
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [showPw, setShowPw] = React.useState(false);
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);

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

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </div>

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

          <div className="login-or">or</div>

          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <Button type="button" variant="secondary" size="lg" className="block" icon="google" onClick={() => setErr(SOCIAL_MSG)}>Continue with Google</Button>
            <Button type="button" variant="secondary" size="lg" className="block" icon="github" onClick={() => setErr(SOCIAL_MSG)}>Continue with GitHub</Button>
          </div>

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
function SignUpScreen({ onSignUp, onSwitch }) {
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);

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

  return (
    <div className="login-shell">
      <div className="login-form-side">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/assets/logo-mark.svg" width="26" height="26" alt="" />
          <span style={{ fontWeight: 600, fontSize: 16, letterSpacing: "-0.018em" }}>JobHub</span>
        </div>

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

          <div className="login-or">or</div>

          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <Button type="button" variant="secondary" size="lg" className="block" icon="google" onClick={() => setErr(SOCIAL_MSG)}>Continue with Google</Button>
            <Button type="button" variant="secondary" size="lg" className="block" icon="github" onClick={() => setErr(SOCIAL_MSG)}>Continue with GitHub</Button>
          </div>

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
function LoginModal({ onClose, onLogin, onSignUp, jobTitle, reason }) {
  const [mode, setMode] = React.useState("login"); // login or signup
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [name, setName] = React.useState("");
  const [err, setErr] = React.useState("");
  const [busy, setBusy] = React.useState(false);

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

        <div className="login-or">or</div>

        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <Button type="button" variant="secondary" className="block" icon="google" onClick={() => setErr(SOCIAL_MSG)}>Continue with Google</Button>
          <Button type="button" variant="secondary" className="block" icon="github" onClick={() => setErr(SOCIAL_MSG)}>Continue with GitHub</Button>
        </div>
      </form>
    </Modal>
  );
}

export { LoginScreen, SignUpScreen, LoginModal };
