/**
 * Component tests for the Two-factor auth settings row.
 * Cases: TC-FE-2FA-01..04 (docs/specs/0133-test-cases.md, section 5.2)
 *
 * TC-FE-2FA-01: Enable toggle calls setup API, shows QR code.
 * TC-FE-2FA-02: Verify-setup shows backup codes on success.
 * TC-FE-2FA-03: Disable requires TOTP code input.
 * TC-FE-2FA-04: Toggle reflects twoFactorEnabled from account.
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
}));

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn().mockResolvedValue(null),
  updateNotificationPreferences: vi.fn(),
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

import { setupTwoFactor, verifyTwoFactorSetup, disableTwoFactor } from "../../api/auth.js";
import { SettingsScreen } from "../../screens/SavedSettings.jsx";

const ACCOUNT_NO_2FA = {
  id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com",
  emailVerified: true, twoFactorEnabled: false,
};

const ACCOUNT_2FA = {
  id: "u2", firstName: "Ana", lastName: "Lee", email: "ana@example.com",
  emailVerified: true, twoFactorEnabled: true,
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

// ── TC-FE-2FA-04: toggle reflects twoFactorEnabled from account ───────────────

describe("TC-FE-2FA-04: the toggle reflects twoFactorEnabled from the account", () => {
  it("shows the toggle OFF when twoFactorEnabled is false", () => {
    renderSettings(ACCOUNT_NO_2FA);
    expect(screen.getByRole("switch", { name: /two-factor auth/i })).toHaveAttribute("aria-checked", "false");
  });

  it("shows the toggle ON when twoFactorEnabled is true", () => {
    renderSettings(ACCOUNT_2FA);
    expect(screen.getByRole("switch", { name: /two-factor auth/i })).toHaveAttribute("aria-checked", "true");
  });
});

// ── TC-FE-2FA-01: enable toggle calls setup API, shows QR code ────────────────

describe("TC-FE-2FA-01: enabling 2FA calls the setup API and shows a QR code", () => {
  it("calls setupTwoFactor and renders a QR code image from the otpauthUri", async () => {
    setupTwoFactor.mockResolvedValueOnce({
      otpauthUri: "otpauth://totp/JobHub:jo@example.com?secret=ABCDEF&issuer=JobHub",
      setupKey: "ABCDEF",
    });

    renderSettings(ACCOUNT_NO_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    await waitFor(() => {
      expect(setupTwoFactor).toHaveBeenCalledTimes(1);
    });

    const qr = await screen.findByAltText(/scan this qr code/i);
    expect(qr.tagName).toBe("IMG");
    expect(qr.getAttribute("src")).toContain(encodeURIComponent("otpauth://totp/JobHub:jo@example.com?secret=ABCDEF&issuer=JobHub"));

    // Manual setup key is also shown as a fallback
    expect(screen.getByDisplayValue("ABCDEF")).toBeInTheDocument();
  });

  it("shows an 'already active' message instead of a QR code on 409", async () => {
    setupTwoFactor.mockRejectedValueOnce({ status: 409, message: "2FA already enabled." });

    renderSettings(ACCOUNT_NO_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    await waitFor(() => {
      expect(screen.getByText(/already active/i)).toBeInTheDocument();
    });
    expect(screen.queryByAltText(/scan this qr code/i)).not.toBeInTheDocument();
  });
});

// ── TC-FE-2FA-02: verify-setup shows backup codes on success ─────────────────

describe("TC-FE-2FA-02: verify-setup shows backup codes on success", () => {
  it("calls verifyTwoFactorSetup with the entered code and shows 8 backup codes", async () => {
    setupTwoFactor.mockResolvedValueOnce({
      otpauthUri: "otpauth://totp/JobHub:jo@example.com?secret=ABCDEF&issuer=JobHub",
      setupKey: "ABCDEF",
    });
    const codes = ["AAAA1111", "BBBB2222", "CCCC3333", "DDDD4444", "EEEE5555", "FFFF6666", "GGGG7777", "HHHH8888"];
    verifyTwoFactorSetup.mockResolvedValueOnce({ backupCodes: codes });

    renderSettings(ACCOUNT_NO_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(verifyTwoFactorSetup).toHaveBeenCalledWith({ totpCode: "123456" });
    });

    for (const code of codes) {
      expect(screen.getByText(code)).toBeInTheDocument();
    }
  });

  it("shows an error and allows retry on a wrong code (400)", async () => {
    setupTwoFactor.mockResolvedValueOnce({
      otpauthUri: "otpauth://totp/JobHub:jo@example.com?secret=ABCDEF&issuer=JobHub",
      setupKey: "ABCDEF",
    });
    verifyTwoFactorSetup.mockRejectedValueOnce({ status: 400, message: "Invalid code." });

    renderSettings(ACCOUNT_NO_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "000000");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/invalid|expired/i);
    });
    // Still able to retry: the code input is present
    expect(screen.getByLabelText(/authentication code/i)).toBeInTheDocument();
  });
});

// ── TC-FE-2FA-03: disable requires TOTP code input ────────────────────────────

describe("TC-FE-2FA-03: disabling 2FA requires a TOTP code input", () => {
  it("shows a confirmation modal with a code input when toggling off", async () => {
    renderSettings(ACCOUNT_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    expect(await screen.findByLabelText(/authentication code/i)).toBeInTheDocument();
    expect(disableTwoFactor).not.toHaveBeenCalled();
  });

  it("calls disableTwoFactor with the entered code on confirm", async () => {
    disableTwoFactor.mockResolvedValueOnce(undefined);
    renderSettings(ACCOUNT_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "654321");
    await userEvent.click(screen.getByRole("button", { name: /disable/i }));

    await waitFor(() => {
      expect(disableTwoFactor).toHaveBeenCalledWith({ totpCode: "654321" });
    });
  });

  it("shows an error and allows retry on a wrong code (401)", async () => {
    disableTwoFactor.mockRejectedValueOnce({ status: 401, message: "Invalid code." });
    renderSettings(ACCOUNT_2FA);
    await userEvent.click(screen.getByRole("switch", { name: /two-factor auth/i }));

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "000000");
    await userEvent.click(screen.getByRole("button", { name: /disable/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/incorrect/i);
    });
    expect(screen.getByLabelText(/authentication code/i)).toBeInTheDocument();
  });
});
