/**
 * Component tests for the "Change password" settings row.
 * Cases: TC-FE-PWD-01..08 (docs/specs/0133-test-cases.md, section 5.1)
 *
 * TC-FE-PWD-01: Submit calls changePassword API with correct payload.
 * TC-FE-PWD-02: API success shows "Password updated" state.
 * TC-FE-PWD-03: API 401 (wrong current password) shows error.
 * TC-FE-PWD-04: Network error shows error, form not cleared.
 * TC-FE-PWD-05: Client-side validation: short password blocks submit.
 * TC-FE-PWD-06: Client-side validation: mismatched passwords blocks submit.
 * TC-FE-PWD-07: 2FA user sees TOTP code field in password change modal.
 * TC-FE-PWD-08: TOTP code included in API call when 2FA enabled.
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/auth.js", () => ({
  changePassword: vi.fn(),
}));

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn().mockResolvedValue(null),
  updateNotificationPreferences: vi.fn(),
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

import { changePassword } from "../../api/auth.js";
import { SettingsScreen } from "../../screens/SavedSettings.jsx";

const ACCOUNT_NO_2FA = {
  id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com",
  emailVerified: true, twoFactorEnabled: false,
};

const ACCOUNT_2FA = {
  id: "u2", firstName: "Ana", lastName: "Lee", email: "ana@example.com",
  emailVerified: true, twoFactorEnabled: true,
};

async function openChangePasswordModal(account = ACCOUNT_NO_2FA) {
  render(
    <SettingsScreen authed={true} account={account} onLogout={vi.fn()} onLogin={vi.fn()} openSearch={vi.fn()} pushToast={vi.fn()} />
  );
  await userEvent.click(screen.getByRole("button", { name: /Change password/i }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

// ── TC-FE-PWD-01: submit calls changePassword API with correct payload ────────

describe("TC-FE-PWD-01: submit calls changePassword API with correct payload", () => {
  it("calls changePassword({currentPassword, newPassword}) on submit", async () => {
    changePassword.mockResolvedValueOnce(undefined);
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(changePassword).toHaveBeenCalledWith({
        currentPassword: "oldpassword1",
        newPassword: "newpassword1",
      });
    });
  });
});

// ── TC-FE-PWD-02: API success shows "Password updated" state ──────────────────

describe("TC-FE-PWD-02: API success shows the 'Password updated' state", () => {
  it("shows success state after changePassword resolves", async () => {
    changePassword.mockResolvedValueOnce(undefined);
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByText("Password updated")).toBeInTheDocument();
    });
  });
});

// ── TC-FE-PWD-03: API 401 shows error ──────────────────────────────────────────

describe("TC-FE-PWD-03: API 401 (wrong current password) shows an error", () => {
  it("shows an error message and does not show the success state", async () => {
    changePassword.mockRejectedValueOnce({ status: 401, message: "Current password is incorrect." });
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "wrongpassword");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByText(/current password is incorrect/i)).toBeInTheDocument();
    });
    expect(screen.queryByText("Password updated")).not.toBeInTheDocument();
  });
});

// ── TC-FE-PWD-04: network error shows error, form not cleared ────────────────

describe("TC-FE-PWD-04: network/server error surfaces honestly without clearing the form", () => {
  it("shows an error and keeps the typed values on a network error", async () => {
    changePassword.mockRejectedValueOnce({ status: 0, message: "Network error" });
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    });

    expect(screen.getByPlaceholderText("••••••••")).toHaveValue("oldpassword1");
    expect(screen.getByPlaceholderText("New password")).toHaveValue("newpassword1");
    expect(screen.getByPlaceholderText("Repeat new password")).toHaveValue("newpassword1");
  });

  it("shows an error on a 500 server error too", async () => {
    changePassword.mockRejectedValueOnce({ status: 500, message: "Internal server error" });
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    });
  });
});

// ── TC-FE-PWD-05: short password blocks submit ────────────────────────────────

describe("TC-FE-PWD-05: client-side validation blocks a too-short new password", () => {
  it("does not call the API and shows a validation error", async () => {
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "short1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "short1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/at least 8 characters/i);
    });
    expect(changePassword).not.toHaveBeenCalled();
  });
});

// ── TC-FE-PWD-06: mismatched passwords blocks submit ──────────────────────────

describe("TC-FE-PWD-06: client-side validation blocks mismatched new password / confirmation", () => {
  it("does not call the API and shows a mismatch error", async () => {
    await openChangePasswordModal();

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "differentpw1");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(screen.getByText(/don't match/i)).toBeInTheDocument();
    });
    expect(changePassword).not.toHaveBeenCalled();
  });
});

// ── TC-FE-PWD-07: 2FA user sees TOTP code field ───────────────────────────────

describe("TC-FE-PWD-07: 2FA user sees a TOTP code field in the password change modal", () => {
  it("renders a TOTP code input when account.twoFactorEnabled is true", async () => {
    await openChangePasswordModal(ACCOUNT_2FA);

    expect(screen.getByLabelText(/authentication code/i)).toBeInTheDocument();
  });

  it("does NOT render a TOTP code input when account.twoFactorEnabled is false", async () => {
    await openChangePasswordModal(ACCOUNT_NO_2FA);

    expect(screen.queryByLabelText(/authentication code/i)).not.toBeInTheDocument();
  });
});

// ── TC-FE-PWD-08: TOTP code included in API call when 2FA enabled ────────────

describe("TC-FE-PWD-08: TOTP code is included in the API call when 2FA is enabled", () => {
  it("calls changePassword with totpCode included", async () => {
    changePassword.mockResolvedValueOnce(undefined);
    await openChangePasswordModal(ACCOUNT_2FA);

    await userEvent.type(screen.getByPlaceholderText("••••••••"), "oldpassword1");
    await userEvent.type(screen.getByPlaceholderText("New password"), "newpassword1");
    await userEvent.type(screen.getByPlaceholderText("Repeat new password"), "newpassword1");
    await userEvent.type(screen.getByLabelText(/authentication code/i), "123456");
    await userEvent.click(screen.getByRole("button", { name: /Update password/i }));

    await waitFor(() => {
      expect(changePassword).toHaveBeenCalledWith({
        currentPassword: "oldpassword1",
        newPassword: "newpassword1",
        totpCode: "123456",
      });
    });
  });
});
