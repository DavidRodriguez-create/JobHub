/**
 * Component tests for Settings > Account email verification status + resend action.
 * Cases: TC-301-01..11 (issue #315, story #301)
 *
 * TC-301-01: Verified badge shown when emailVerified is true.
 * TC-301-02: "Not verified" badge + "Verify now" action shown when emailVerified is false.
 * TC-301-03: Clicking "Verify now" calls resendVerification(email) and reveals the code input.
 * TC-301-04: Submitting a valid 6-digit code calls verifyEmail and refreshes the account.
 * TC-301-05: Invalid/rejected code (400) shows an error and keeps the input for retry.
 * TC-301-06: 429 on resend shows "Try again later".
 * TC-301-07: 429 on verify (code submit) shows "Try again later".
 * TC-301-08: "Verify now" is disabled / shows a busy state while resend is in flight.
 * TC-301-09: Code input rejects/ignores non-digit or wrong-length input.
 * TC-301-10: Account still loading (account is null/undefined) does not crash.
 * TC-301-11: Account present but email is falsy/empty.
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/auth.js", () => ({
  changePassword: vi.fn(),
  setupTwoFactor: vi.fn(),
  verifyTwoFactorSetup: vi.fn(),
  disableTwoFactor: vi.fn(),
  resendVerification: vi.fn(),
  verifyEmail: vi.fn(),
}));

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn().mockResolvedValue(null),
  updateNotificationPreferences: vi.fn(),
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

import { resendVerification, verifyEmail } from "../../api/auth.js";
import { SettingsScreen } from "../../screens/SavedSettings.jsx";

const ACCOUNT_VERIFIED = {
  id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com",
  emailVerified: true, twoFactorEnabled: false,
};
const ACCOUNT_UNVERIFIED = {
  id: "u2", firstName: "Ana", lastName: "Lee", email: "ana@example.com",
  emailVerified: false, twoFactorEnabled: false,
};

function renderSettings(account) {
  return render(
    <SettingsScreen authed={true} account={account} onLogout={vi.fn()} onLogin={vi.fn()} openSearch={vi.fn()} pushToast={vi.fn()} />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

// ── TC-301-01: verified badge shown when emailVerified is true ───────────────

describe("TC-301-01: verified badge shown when emailVerified is true", () => {
  it("shows a 'Verified' badge and no 'Verify now' action", () => {
    renderSettings(ACCOUNT_VERIFIED);

    expect(screen.getByText(/^verified$/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /verify now/i })).not.toBeInTheDocument();
    expect(resendVerification).not.toHaveBeenCalled();
  });
});

// ── TC-301-02: not-verified badge + Verify now action shown ──────────────────

describe("TC-301-02: 'Not verified' badge + 'Verify now' action shown when emailVerified is false", () => {
  it("shows a 'Not verified' badge, a 'Verify now' button, and no code input yet", () => {
    renderSettings(ACCOUNT_UNVERIFIED);

    expect(screen.getByText(/not verified/i)).toBeInTheDocument();
    const verifyNowBtn = screen.getByRole("button", { name: /verify now/i });
    expect(verifyNowBtn).toBeInTheDocument();
    expect(verifyNowBtn).toBeEnabled();
    expect(screen.queryByPlaceholderText("123456")).not.toBeInTheDocument();
  });
});

// ── TC-301-03: clicking Verify now calls resendVerification and reveals code input ──

describe("TC-301-03: clicking 'Verify now' calls resendVerification(email) and reveals the code input", () => {
  it("calls resendVerification with the account's own email and shows the code input", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));

    await waitFor(() => {
      expect(resendVerification).toHaveBeenCalledTimes(1);
      expect(resendVerification).toHaveBeenCalledWith("ana@example.com");
    });

    const codeInput = await screen.findByPlaceholderText("123456");
    expect(codeInput).toBeInTheDocument();
    expect(codeInput).toHaveAttribute("inputMode", "numeric");
    expect(codeInput).toHaveAttribute("maxLength", "6");
    expect(screen.getByRole("button", { name: /verify|submit code/i })).toBeInTheDocument();
  });
});

// ── TC-301-04: valid code submit calls verifyEmail and refreshes the account ─────

describe("TC-301-04: submitting a valid 6-digit code calls verifyEmail and refreshes the account", () => {
  it("calls verifyEmail with {email, code} and flips the badge to Verified", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    verifyEmail.mockResolvedValueOnce(undefined);
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));
    const codeInput = await screen.findByPlaceholderText("123456");
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify|submit code/i }));

    await waitFor(() => {
      expect(verifyEmail).toHaveBeenCalledTimes(1);
      expect(verifyEmail).toHaveBeenCalledWith({ email: "ana@example.com", code: "123456" });
    });

    await waitFor(() => {
      expect(screen.getByText(/^verified$/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/not verified/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /verify now/i })).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("123456")).not.toBeInTheDocument();
  });
});

// ── TC-301-05: invalid/rejected code (400) shows error, keeps input for retry ────

describe("TC-301-05: invalid/rejected code (400) shows an error and keeps the input for retry", () => {
  it("shows an invalid/expired error and leaves the code input open", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    verifyEmail.mockRejectedValueOnce({ status: 400, message: "Code invalid or expired." });
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));
    const codeInput = await screen.findByPlaceholderText("123456");
    await userEvent.type(codeInput, "000000");
    await userEvent.click(screen.getByRole("button", { name: /verify|submit code/i }));

    await waitFor(() => {
      const alert = screen.getByRole("alert");
      expect(alert.textContent).toMatch(/invalid|expired/i);
    });

    expect(screen.getByPlaceholderText("123456")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("123456")).toBeEnabled();
    expect(screen.getByText(/not verified/i)).toBeInTheDocument();
    expect(verifyEmail).toHaveBeenCalledTimes(1);
  });
});

// ── TC-301-06: 429 on resend shows Try again later ────────────────────────────

describe("TC-301-06: 429 on resend shows 'Try again later'", () => {
  it("shows a try-again-later message and does not reveal the code input", async () => {
    resendVerification.mockRejectedValueOnce({ status: 429, message: "Too many requests." });
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));

    await waitFor(() => {
      expect(screen.getByText(/try again later/i)).toBeInTheDocument();
    });
    expect(screen.queryByPlaceholderText("123456")).not.toBeInTheDocument();
  });
});

// ── TC-301-07: 429 on verify (code submit) shows Try again later ─────────────

describe("TC-301-07: 429 on verify (code submit) shows 'Try again later'", () => {
  it("shows a try-again-later message and keeps the code input for retry", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    verifyEmail.mockRejectedValueOnce({ status: 429, message: "Too many attempts." });
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));
    const codeInput = await screen.findByPlaceholderText("123456");
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify|submit code/i }));

    await waitFor(() => {
      expect(screen.getByText(/try again later/i)).toBeInTheDocument();
    });
    expect(screen.getByPlaceholderText("123456")).toBeInTheDocument();
    expect(screen.getByText(/not verified/i)).toBeInTheDocument();
  });
});

// ── TC-301-08: Verify now busy-guards while resend is in flight ──────────────

describe("TC-301-08: 'Verify now' is disabled / shows a busy state while resend is in flight", () => {
  it("does not fire a second resendVerification call on rapid double click", async () => {
    let resolveResend;
    resendVerification.mockImplementationOnce(
      () => new Promise((resolve) => { resolveResend = resolve; })
    );
    renderSettings(ACCOUNT_UNVERIFIED);

    const verifyNowBtn = screen.getByRole("button", { name: /verify now/i });
    await userEvent.click(verifyNowBtn);
    await userEvent.click(verifyNowBtn);

    await waitFor(() => {
      expect(resendVerification).toHaveBeenCalledTimes(1);
    });
    expect(verifyNowBtn).toBeDisabled();

    resolveResend();
    await waitFor(() => {
      expect(screen.getByPlaceholderText("123456")).toBeInTheDocument();
    });
  });
});

// ── TC-301-09: code input rejects/ignores non-digit or wrong-length input ────

describe("TC-301-09: code input rejects/ignores non-digit or wrong-length input", () => {
  it("does not call verifyEmail for a 4-digit code (submit is a no-op)", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));
    const codeInput = await screen.findByPlaceholderText("123456");
    await userEvent.type(codeInput, "1234");

    const submitBtn = screen.getByRole("button", { name: /verify|submit code/i });
    expect(submitBtn).toBeDisabled();
    await userEvent.click(submitBtn);

    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it("strips non-numeric characters as the user types and does not call verifyEmail for a malformed value", async () => {
    resendVerification.mockResolvedValueOnce(undefined);
    renderSettings(ACCOUNT_UNVERIFIED);

    await userEvent.click(screen.getByRole("button", { name: /verify now/i }));
    const codeInput = await screen.findByPlaceholderText("123456");
    await userEvent.type(codeInput, "12a45b");

    expect(codeInput).toHaveValue("1245");

    const submitBtn = screen.getByRole("button", { name: /verify|submit code/i });
    expect(submitBtn).toBeDisabled();
    await userEvent.click(submitBtn);

    expect(verifyEmail).not.toHaveBeenCalled();
  });
});

// ── TC-301-10: account still loading (null/undefined) does not crash ─────────

describe("TC-301-10: account still loading (account is null/undefined) does not crash and shows no badge prematurely", () => {
  it("renders without throwing and issues no resend/verify calls when account is null", () => {
    expect(() => renderSettings(null)).not.toThrow();

    expect(screen.queryByText(/^verified$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/not verified/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /verify now/i })).not.toBeInTheDocument();
    expect(resendVerification).not.toHaveBeenCalled();
    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it("renders without throwing when account is undefined", () => {
    expect(() => renderSettings(undefined)).not.toThrow();
    expect(resendVerification).not.toHaveBeenCalled();
  });
});

// ── TC-301-11: account present but email is falsy/empty ──────────────────────

describe("TC-301-11: account present but email is falsy/empty", () => {
  it("does not call resendVerification with an empty email", async () => {
    const accountNoEmail = { ...ACCOUNT_UNVERIFIED, email: "" };
    renderSettings(accountNoEmail);

    const verifyNowBtn = screen.queryByRole("button", { name: /verify now/i });
    if (verifyNowBtn && !verifyNowBtn.disabled) {
      await userEvent.click(verifyNowBtn);
    }

    expect(resendVerification).not.toHaveBeenCalledWith("");
  });
});
