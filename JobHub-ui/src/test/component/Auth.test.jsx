/**
 * Story #459 (ADR 0027): social login button wiring on the three existing Auth.jsx
 * surfaces (full-page Login screen, full-page Sign Up screen, Login modal).
 * Cases: TC-459-D1..D6 (docs/qa/459-social-login-test-cases.md, section D.1).
 *
 * Story #506 (ADR 0028): provider availability gates each button, the "or" divider
 * degrades with it, and the login/sign-up logo now navigates home.
 * Cases: TC-506-D1..D22, D24, D26 (docs/qa/506-oauth-provider-availability-test-cases.md,
 * sections D.1/D.2/D.3/D.4).
 *
 * TC-459-D1/D2/D3 (OAUTH-UI-1): "Continue with Google/GitHub" render on all three
 *   surfaces once availability resolves with both providers available; the old
 *   SOCIAL_MSG placeholder text is gone.
 * TC-459-D4/D5 (OAUTH-UI-2): clicking a button calls startOAuth(provider) and redirects
 *   the browser to the resolved authorizationUrl.
 * TC-459-D6 (OAUTH-UI-2, start failure): a rejected startOAuth() shows an inline error via
 *   the existing FormError pattern and never redirects.
 *
 * Rework (P3 architect review of #511): Auth.jsx is now presentational. LoginScreen,
 * SignUpScreen and LoginModal take `availability` as a prop instead of running their own
 * fetch (no module-level cache, no test-only reset hook). App.jsx is the single owner of
 * the GET /auth/oauth/providers call and threads the resolved map down.
 *
 * TC-506-D1..D12/D19/D21/D22/D24/D26 and the TC-459 cases exercise that presentational
 * contract directly: render LoginScreen/SignUpScreen/LoginModal with an explicit
 * `availability` prop (the shape App.jsx would have computed/passed in that scenario) and
 * assert the rendered buttons/divider/logo behaviour.
 *
 * TC-506-D13/D16/D20 render the real <App /> (mirroring OAuthCallback.test.jsx /
 * LoginTwoFactor.test.jsx's mocking strategy) to prove the actual production wiring: the
 * in-flight default (D13), the fail-open decision on a rejected call (D16), and — the
 * case the architect flagged as now STRONGER than before — that switching between two
 * auth surfaces in one App session issues exactly one GET call (D20), which the old
 * module-level memoized promise never actually proved (it only proved memoization within
 * Auth.jsx, not what App.jsx as the real caller does).
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/auth.js", () => ({
  login: vi.fn(),
  loginTwoFactor: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  requestVerification: vi.fn(),
  startOAuth: vi.fn(),
  completeOAuthLogin: vi.fn(),
  getOAuthProviders: vi.fn(),
  getApplyProfile: vi.fn().mockResolvedValue(null),
}));

vi.mock("../../api/config.js", () => ({ USE_API: false }));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

// Only needed for the <App /> based cases (D13/D16/D20) below: with USE_API mocked
// false, App.jsx's own boot/data effects no-op, so the only real risk on the transient
// first paint (route starts at "search" before the sessionStorage-restore effect runs)
// is mounting the real JobSearchScreen. Stub it out like the other App-level test files
// do (OAuthCallback.test.jsx, LoginTwoFactor.test.jsx).
vi.mock("../../screens/JobSearch.jsx", () => ({
  JobSearchScreen: () => <div data-testid="screen-search">Search</div>,
  JobDetailDrawer: () => null,
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

import * as authApi from "../../api/auth.js";
import {
  LoginScreen, SignUpScreen, LoginModal, HIDE_ALL_PROVIDERS, SHOW_ALL_PROVIDERS,
} from "../../screens/Auth.jsx";
import App from "../../App.jsx";

const SOCIAL_MSG = "Social sign-in isn't available yet";
const ROUTE_KEY = "jobhub_route";

const ONLY_GOOGLE = { google: true, github: false };
const ONLY_GITHUB = { google: false, github: true };

function bothAvailableApiResponse() {
  return { providers: [{ provider: "google", available: true }, { provider: "github", available: true }] };
}

// Never resolves — simulates the in-flight window before the first response lands.
function pendingForever() {
  return new Promise(() => {});
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  // jsdom throws "Not implemented: navigation" if we let a real assignment through;
  // replace window.location with a plain writable stand-in so tests can assert the
  // redirect target without a real navigation attempt.
  delete window.location;
  window.location = { href: "" };
});

// ── TC-506-D1/D2/D3 (UI-AVAIL-1): both providers available ─────────────────────

describe("TC-506-D1/TC-459-D1: Login screen renders the social buttons", () => {
  it("shows Continue with Google/GitHub and the 'or' divider when both are available", () => {
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(SOCIAL_MSG, "i"))).not.toBeInTheDocument();
  });
});

describe("TC-506-D2/TC-459-D2: Sign Up screen renders the social buttons", () => {
  it("shows Continue with Google/GitHub and the 'or' divider when both are available", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(SOCIAL_MSG, "i"))).not.toBeInTheDocument();
  });
});

describe("TC-506-D3/TC-459-D3: Login modal renders the social buttons", () => {
  it("shows Continue with Google/GitHub and the 'or' divider when both are available", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} reason="saved" availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(SOCIAL_MSG, "i"))).not.toBeInTheDocument();
  });
});

// ── TC-506-D4/D5/D6 (UI-AVAIL-2/UI-AVAIL-8): only Google available ─────────────

describe("TC-506-D4: Login screen — only Google available", () => {
  it("shows only Continue with Google; GitHub is absent, not disabled; divider stays", () => {
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} availability={ONLY_GOOGLE} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
  });
});

describe("TC-506-D5: Sign Up screen — only Google available", () => {
  it("shows only Continue with Google; GitHub is absent", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={ONLY_GOOGLE} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
  });
});

describe("TC-506-D6: Login modal — only Google available", () => {
  it("shows only Continue with Google; GitHub is absent", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} availability={ONLY_GOOGLE} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
  });
});

// ── TC-506-D7/D8/D9 (UI-AVAIL-3/UI-AVAIL-8): only GitHub available ─────────────

describe("TC-506-D7: Login screen — only GitHub available", () => {
  it("shows only Continue with GitHub; Google is absent, not disabled; divider stays", () => {
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} availability={ONLY_GITHUB} />);

    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
  });
});

describe("TC-506-D8: Sign Up screen — only GitHub available", () => {
  it("shows only Continue with GitHub; Google is absent", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={ONLY_GITHUB} />);

    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
  });
});

describe("TC-506-D9: Login modal — only GitHub available", () => {
  it("shows only Continue with GitHub; Google is absent", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} availability={ONLY_GITHUB} />);

    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
  });
});

// ── TC-506-D10/D11/D12 (UI-AVAIL-4): neither available, no orphan divider ──────

describe("TC-506-D10: Login screen — neither provider available", () => {
  it("shows no social buttons and no 'or' divider; the password form is unaffected", () => {
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} availability={HIDE_ALL_PROVIDERS} />);

    expect(screen.queryByText("or")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    expect(screen.getByText(/create an account/i)).toBeInTheDocument();
  });
});

describe("TC-506-D11: Sign Up screen — neither provider available", () => {
  it("shows no social buttons and no 'or' divider; the form is unaffected", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={HIDE_ALL_PROVIDERS} />);

    expect(screen.queryByText("or")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.getByText(/sign in/i)).toBeInTheDocument();
  });
});

describe("TC-506-D12: Login modal — neither provider available", () => {
  it("shows no social buttons and no 'or' divider; the modal form is unaffected", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} availability={HIDE_ALL_PROVIDERS} />);

    expect(screen.queryByText("or")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
  });
});

// ── TC-506-D13/D14/D15 (UI-AVAIL-5): in-flight window renders like "neither" ────

describe("TC-506-D13: Login screen — availability check still in flight", () => {
  it("renders no social buttons and no divider before <App />'s fetch resolves", () => {
    authApi.getOAuthProviders.mockImplementationOnce(pendingForever);
    sessionStorage.setItem(ROUTE_KEY, "login");

    render(<App />);

    expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.queryByText("or")).not.toBeInTheDocument();
  });
});

describe("TC-506-D14: Sign Up screen — availability check still in flight", () => {
  it("renders no social buttons and no divider with the default (not-yet-resolved) availability", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} />);

    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.queryByText("or")).not.toBeInTheDocument();
  });
});

describe("TC-506-D15: Login modal — availability check still in flight", () => {
  it("renders no social buttons and no divider with the default (not-yet-resolved) availability", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} />);

    expect(screen.queryByRole("button", { name: /continue with google/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.queryByText("or")).not.toBeInTheDocument();
  });
});

// ── TC-506-D16/D17/D18 (UI-AVAIL-6): fail open on error/non-200 ────────────────

describe("TC-506-D16: Login screen — availability check fails, fail open", () => {
  it("shows both buttons and the divider once <App />'s fetch rejects", async () => {
    authApi.getOAuthProviders.mockRejectedValueOnce({ status: 500, message: "boom" });
    sessionStorage.setItem(ROUTE_KEY, "login");

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
  });
});

describe("TC-506-D17: Sign Up screen — availability check fails, fail open", () => {
  it("shows both buttons and the divider given the fail-open availability", () => {
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
  });
});

describe("TC-506-D18: Login modal — availability check fails, fail open", () => {
  it("shows both buttons and the divider given the fail-open availability", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /continue with github/i })).toBeInTheDocument();
    expect(screen.getByText("or")).toBeInTheDocument();
  });
});

// ── TC-506-D19 (UI-AVAIL-7): modal's other content unaffected ──────────────────

describe("TC-506-D19: Login modal context banner + form are unaffected by availability", () => {
  it("keeps the apply-context banner and primary submit button while only the social block changes", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} jobTitle="Senior Engineer at Acme" availability={ONLY_GOOGLE} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /continue with github/i })).not.toBeInTheDocument();
    expect(screen.getByText("Senior Engineer at Acme")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^sign in$/i })).toBeInTheDocument();
  });
});

// ── TC-506-D20 (AVAIL-8): fetched once, not once per surface ───────────────────
//
// Stronger than the old module-cache test: this renders the real <App />, which is
// the actual (and only) caller of GET /auth/oauth/providers now, and switches between
// two real auth surfaces (Login -> Sign Up) within the same session to prove App's
// single effect — not a memoized promise inside Auth.jsx — is what keeps this to one
// call.

describe("TC-506-D20: availability is fetched once, shared across surfaces", () => {
  it("calls getOAuthProviders exactly once as <App /> moves from the Login to the Sign Up screen", async () => {
    authApi.getOAuthProviders.mockResolvedValueOnce(bothAvailableApiResponse());
    sessionStorage.setItem(ROUTE_KEY, "login");

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    });
    expect(authApi.getOAuthProviders).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByText(/create an account/i));

    await waitFor(() => {
      expect(screen.getByText(/create your account/i)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(authApi.getOAuthProviders).toHaveBeenCalledTimes(1);
  });
});

// ── TC-506-D21/D22 (LOGO-1/LOGO-2): logo navigates home ─────────────────────────

describe("TC-506-D21: Login screen logo navigates home", () => {
  it("calls onLogoClick exactly once, with no form submission or api call", async () => {
    const onLogoClick = vi.fn();
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} onLogoClick={onLogoClick} availability={SHOW_ALL_PROVIDERS} />);

    await userEvent.click(screen.getByRole("button", { name: /jobhub home/i }));

    expect(onLogoClick).toHaveBeenCalledTimes(1);
    expect(authApi.startOAuth).not.toHaveBeenCalled();
  });
});

describe("TC-506-D22: Sign Up screen logo navigates home", () => {
  it("calls onLogoClick exactly once, with no form submission or api call", async () => {
    const onLogoClick = vi.fn();
    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} onLogoClick={onLogoClick} availability={SHOW_ALL_PROVIDERS} />);

    await userEvent.click(screen.getByRole("button", { name: /jobhub home/i }));

    expect(onLogoClick).toHaveBeenCalledTimes(1);
    expect(authApi.startOAuth).not.toHaveBeenCalled();
  });
});

// ── TC-506-D24 (LOGO-4): nothing to wire on the Login modal ─────────────────────

describe("TC-506-D24: Login modal has no clickable logo element", () => {
  it("renders no logo/home button in the modal", () => {
    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);

    expect(screen.getByRole("button", { name: /continue with google/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /jobhub home/i })).not.toBeInTheDocument();
  });
});

// ── TC-506-D26 (LOGO-6): logo click doesn't interfere with an in-flight sign-in ─

describe("TC-506-D26: clicking the logo does not interfere with an in-flight social sign-in", () => {
  it("still navigates via the logo without a second startOAuth call or an unmounted-state warning", async () => {
    authApi.startOAuth.mockImplementationOnce(pendingForever);
    const onLogoClick = vi.fn();
    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} onLogoClick={onLogoClick} availability={SHOW_ALL_PROVIDERS} />);

    await userEvent.click(screen.getByRole("button", { name: /continue with google/i }));
    expect(authApi.startOAuth).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole("button", { name: /jobhub home/i }));

    expect(onLogoClick).toHaveBeenCalledTimes(1);
    expect(authApi.startOAuth).toHaveBeenCalledTimes(1);
  });
});

// ── TC-459-D4/D5: clicking calls startOAuth(provider) and redirects ─────────────

describe("TC-459-D4: clicking Continue with Google starts the real flow", () => {
  it("calls startOAuth('google') and redirects the browser to authorizationUrl", async () => {
    authApi.startOAuth.mockResolvedValueOnce({ authorizationUrl: "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&state=xyz" });

    render(<LoginScreen onLogin={vi.fn()} onSwitch={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);
    await userEvent.click(screen.getByRole("button", { name: /continue with google/i }));

    await waitFor(() => {
      expect(authApi.startOAuth).toHaveBeenCalledWith("google");
    });
    await waitFor(() => {
      expect(window.location.href).toBe("https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&state=xyz");
    });
    expect(screen.queryByText(new RegExp(SOCIAL_MSG, "i"))).not.toBeInTheDocument();
  });
});

describe("TC-459-D5: clicking Continue with GitHub starts the real flow", () => {
  it("calls startOAuth('github') and redirects the browser to authorizationUrl", async () => {
    authApi.startOAuth.mockResolvedValueOnce({ authorizationUrl: "https://github.com/login/oauth/authorize?client_id=abc&state=xyz" });

    render(<SignUpScreen onSignUp={vi.fn()} onSwitch={vi.fn()} availability={SHOW_ALL_PROVIDERS} />);
    await userEvent.click(screen.getByRole("button", { name: /continue with github/i }));

    await waitFor(() => {
      expect(authApi.startOAuth).toHaveBeenCalledWith("github");
    });
    await waitFor(() => {
      expect(window.location.href).toBe("https://github.com/login/oauth/authorize?client_id=abc&state=xyz");
    });
  });
});

// ── TC-459-D6: start() failure shows an inline error, no redirect ──────────────

describe("TC-459-D6: a failed startOAuth() shows an inline error and does not redirect", () => {
  it("shows a FormError alert and leaves window.location untouched", async () => {
    authApi.startOAuth.mockRejectedValueOnce({ status: 500, message: "Something went wrong. Please try again." });

    render(<LoginModal onClose={vi.fn()} onLogin={vi.fn()} onSignUp={vi.fn()} jobTitle="Senior Engineer at Acme" availability={SHOW_ALL_PROVIDERS} />);
    await userEvent.click(screen.getByRole("button", { name: /continue with google/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(window.location.href).toBe("");
  });
});
