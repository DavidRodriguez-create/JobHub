/**
 * Component tests for the email-verification code-entry screen.
 * Cases: EV-FE-01..07
 *
 * EV-FE-01: After signup with verificationRequired=true, the verify screen is shown.
 * EV-FE-02: Entering a valid 6-digit code and submitting calls verifyEmail({email, code}).
 * EV-FE-03: Backend returns 400 (wrong/expired code) — an error message is shown.
 * EV-FE-04: Resend button calls resendVerification(email) and shows confirmation text.
 * EV-FE-05: Resend returns 429 — a "try again later" message is shown.
 * EV-FE-06: Login with an unverified account (403) shows a "verify your email" prompt.
 * EV-FE-07: Verify success invokes the onVerified callback.
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ── Mock the auth API module ───────────────────────────────────────────────────
// vi.mock is hoisted; the factory runs before imports below.
vi.mock("../../api/auth.js", () => ({
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  getToken: vi.fn(() => null),
  setToken: vi.fn(),
  clearToken: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(status, message) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, totalPages: 0 }),
  listSavedJobs: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  getJobFacets: vi.fn().mockResolvedValue({
    companies: [], locations: [], languages: [], employmentTypes: [],
    careerLevels: [], compensationMin: 0, compensationMax: 300000,
  }),
}));

vi.mock("../../api/applications.js", () => ({
  listApplications: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  applicationStats: vi.fn().mockResolvedValue(null),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "Acme", industry: "—", size: "—", hq: "—", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/CommandPalette.jsx", () => ({
  CommandPalette: () => null,
}));

vi.mock("../../components/AddApplication.jsx", () => ({
  AddApplicationModal: () => null,
}));

// ── Import after mocks are registered ─────────────────────────────────────────
import * as authApi from "../../api/auth.js";
import { VerifyEmailScreen, LoginScreen, SignUpScreen } from "../../screens/Auth.jsx";

const { verifyEmail, resendVerification, register } = authApi;

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

// ── EV-FE-01: signup routes to verify-email screen when verificationRequired=true ──
// Tests that: when register() returns verificationRequired=true, the App routes
// to VerifyEmailScreen instead of auto-logging in.
// Strategy: render SignUpScreen directly with a controlled onSignUp that calls
// register and routes to verify screen (mimics App.handleSignup logic).

describe("EV-FE-01: signup routes to verify-email screen when verificationRequired=true", () => {
  it("shows verify-email screen when onSignUp resolves to verificationRequired=true", async () => {
    // We render a harness that mimics what App does after signup:
    // if verificationRequired=true, show VerifyEmailScreen; else show 'done'.
    function SignupHarness() {
      const [view, setView] = React.useState("signup");
      const [pendingEmail, setPendingEmail] = React.useState(null);

      async function handleSignup(name, email, password) {
        const parts = (name || "").trim().split(/\s+/).filter(Boolean);
        const firstName = parts.shift() || "";
        const lastName = parts.join(" ") || "";
        const res = await register({ firstName, lastName, email, password });
        if (res && res.verificationRequired) {
          setPendingEmail(email);
          setView("verify");
        } else {
          setView("done");
        }
      }

      if (view === "verify") {
        return <VerifyEmailScreen email={pendingEmail} onVerified={vi.fn()} onBackToLogin={vi.fn()} />;
      }
      if (view === "done") return <div>done</div>;
      return <SignUpScreen onSignUp={handleSignup} onSwitch={vi.fn()} />;
    }

    register.mockResolvedValueOnce({
      id: "u1", firstName: "Jo", lastName: "S", email: "jo@example.com",
      emailVerified: false, verificationRequired: true,
    });

    render(<SignupHarness />);

    await userEvent.type(screen.getByPlaceholderText(/Jordan Lee/i), "Jo Smith");
    await userEvent.type(screen.getByPlaceholderText(/you@email.com/i), "jo@example.com");
    await userEvent.type(screen.getByPlaceholderText(/At least 8 characters/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /Create account/i }));

    await waitFor(
      () => expect(screen.getByTestId("verify-email-screen")).toBeInTheDocument(),
      { timeout: 3000 }
    );
  });
});

// ── EV-FE-02: submitting valid code calls verifyEmail({email, code}) ──────────

describe("EV-FE-02: submitting a valid code calls verifyEmail({email, code})", () => {
  it("calls verifyEmail with {email, code} on form submit", async () => {
    verifyEmail.mockResolvedValueOnce(undefined);
    const onVerified = vi.fn();

    render(
      <VerifyEmailScreen
        email="jo@example.com"
        onVerified={onVerified}
        onBackToLogin={vi.fn()}
      />
    );

    expect(screen.getByTestId("verify-email-screen")).toBeInTheDocument();

    const codeInput = screen.getByPlaceholderText("123456");
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /Verify/i }));

    await waitFor(() => {
      expect(verifyEmail).toHaveBeenCalledWith({ email: "jo@example.com", code: "123456" });
    });
  });
});

// ── EV-FE-07: verify success calls onVerified ─────────────────────────────────

describe("EV-FE-07: on verify success, onVerified callback is called", () => {
  it("calls onVerified after verifyEmail resolves successfully", async () => {
    verifyEmail.mockResolvedValueOnce(undefined);
    const onVerified = vi.fn();

    render(
      <VerifyEmailScreen
        email="jo@example.com"
        onVerified={onVerified}
        onBackToLogin={vi.fn()}
      />
    );

    const input = screen.getByPlaceholderText("123456");
    await userEvent.type(input, "999888");
    await userEvent.click(screen.getByRole("button", { name: /Verify/i }));

    await waitFor(() => expect(onVerified).toHaveBeenCalledTimes(1));
  });
});

// ── EV-FE-03: 400 response shows invalid/expired error ────────────────────────

describe("EV-FE-03: backend 400 shows invalid/expired error message", () => {
  it("shows an error when verifyEmail throws a 400 error", async () => {
    verifyEmail.mockRejectedValueOnce({ status: 400, message: "Code invalid or expired." });

    render(
      <VerifyEmailScreen
        email="jo@example.com"
        onVerified={vi.fn()}
        onBackToLogin={vi.fn()}
      />
    );

    const input = screen.getByPlaceholderText("123456");
    await userEvent.type(input, "000000");
    await userEvent.click(screen.getByRole("button", { name: /Verify/i }));

    await waitFor(() => {
      const alert = screen.getByRole("alert");
      expect(alert).toBeInTheDocument();
      expect(alert.textContent).toMatch(/invalid|expired/i);
    });
  });
});

// ── EV-FE-04: Resend button calls resendVerification and shows confirmation ───

describe("EV-FE-04: Resend button calls resendVerification(email) and shows confirmation", () => {
  it("calls resendVerification with email and shows confirmation text", async () => {
    resendVerification.mockResolvedValueOnce(undefined);

    render(
      <VerifyEmailScreen
        email="jo@example.com"
        onVerified={vi.fn()}
        onBackToLogin={vi.fn()}
      />
    );

    const resendBtn = screen.getByRole("button", { name: /Resend code/i });
    await userEvent.click(resendBtn);

    await waitFor(() => {
      expect(resendVerification).toHaveBeenCalledWith("jo@example.com");
    });

    // Confirmation text should appear
    await waitFor(() => {
      expect(screen.getByText(/email sent|code sent|check your inbox/i)).toBeInTheDocument();
    });
  });
});

// ── EV-FE-05: Resend 429 shows "try again later" ─────────────────────────────

describe("EV-FE-05: Resend 429 shows 'try again later' message", () => {
  it("shows throttle message when resendVerification throws 429", async () => {
    resendVerification.mockRejectedValueOnce({ status: 429, message: "Too many requests." });

    render(
      <VerifyEmailScreen
        email="jo@example.com"
        onVerified={vi.fn()}
        onBackToLogin={vi.fn()}
      />
    );

    const resendBtn = screen.getByRole("button", { name: /Resend code/i });
    await userEvent.click(resendBtn);

    await waitFor(() => {
      expect(screen.getByText(/try again later/i)).toBeInTheDocument();
    });
  });
});

// ── EV-FE-06: Login 403 shows "verify your email" prompt ─────────────────────

describe("EV-FE-06: Login 403 (unverified) shows verify-your-email message", () => {
  it("LoginScreen shows verify-email message in alert on 403 error", async () => {
    const onLogin = vi.fn().mockRejectedValueOnce({ status: 403, message: "Email not verified." });

    render(
      <LoginScreen
        onLogin={onLogin}
        onSwitch={vi.fn()}
      />
    );

    await userEvent.type(screen.getByPlaceholderText(/you@email.com/i), "jo@example.com");
    await userEvent.type(screen.getByPlaceholderText(/••••••••/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /Sign in/i }));

    await waitFor(() => {
      const alert = screen.getByRole("alert");
      expect(alert.textContent).toMatch(/verify your email|not verified|check your inbox/i);
    });
  });
});
