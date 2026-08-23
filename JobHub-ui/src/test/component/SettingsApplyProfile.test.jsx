/**
 * Component tests for SettingsScreen -> Apply profile section.
 * Story #336 / ticket #422. Cases FE-C1..FE-C16 from
 * docs/qa/336-apply-answer-bank-cases.md, mirroring the mocking pattern in
 * SettingsNotifications.test.jsx.
 *
 * Contract fields (auth-service.yaml, ADR 0022): workAuthorization,
 * requiresSponsorship (bool), noticePeriod, salaryExpectation, currentLocation,
 * willingToRelocate (bool), linkedinUrl, githubUrl, portfolioUrl, languages
 * (string[]), roomToGrow, updatedAt.
 */
import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/auth.js", async () => {
  const actual = await vi.importActual("../../api/auth.js");
  return {
    ...actual,
    getApplyProfile: vi.fn(),
    saveApplyProfile: vi.fn(),
    updateCurrentUser: vi.fn(),
  };
});

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn(() => new Promise(() => {})), // never resolves; keep Notifications inert
  updateNotificationPreferences: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  },
}));

import { getApplyProfile, saveApplyProfile, updateCurrentUser } from "../../api/auth.js";
import { ApiError } from "../../api/client.js";
import { SettingsScreen } from "../../screens/SavedSettings.jsx";

const ACCOUNT = { firstName: "Jo", lastName: "Smith", email: "jo@example.com" };

const EMPTY_PROFILE = {
  workAuthorization: null,
  requiresSponsorship: null,
  noticePeriod: null,
  salaryExpectation: null,
  currentLocation: null,
  willingToRelocate: null,
  linkedinUrl: null,
  githubUrl: null,
  portfolioUrl: null,
  languages: null,
  roomToGrow: null,
  updatedAt: null,
};

const FULL_PROFILE = {
  workAuthorization: "US Citizen",
  requiresSponsorship: false,
  noticePeriod: "2 weeks",
  salaryExpectation: "$120k-$140k",
  currentLocation: "Madrid, Spain",
  willingToRelocate: true,
  linkedinUrl: "https://linkedin.com/in/alice",
  githubUrl: "https://github.com/alice",
  portfolioUrl: "https://alice.dev",
  languages: ["English (native)", "Spanish (C1)"],
  roomToGrow: "Grow into a staff engineer role",
  updatedAt: "2026-07-20T10:00:00Z",
};

function renderSettings(props = {}) {
  return render(
    <SettingsScreen
      authed={true}
      account={ACCOUNT}
      onLogout={vi.fn()}
      onLogin={vi.fn()}
      openSearch={vi.fn()}
      {...props}
    />
  );
}

async function gotoApplyProfile(user) {
  await user.click(screen.getByText("Apply profile"));
}

beforeEach(() => {
  vi.clearAllMocks();
});

// userEvent.setup() installs its own Clipboard stub on navigator.clipboard
// (attachClipboardStubToView), replacing anything assigned beforehand. Spy on
// it AFTER setup() so assertions see the calls the component actually makes.
function spyOnClipboard() {
  return vi.spyOn(navigator.clipboard, "writeText");
}

// ---------------------------------------------------------------------------
// FE-C1: empty state on first load (AC1)
// ---------------------------------------------------------------------------

describe("FE-C1: empty state on first load (AC1)", () => {
  it("renders every field empty, no last-saved indicator, no error banner", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await waitFor(() => expect(getApplyProfile).toHaveBeenCalledTimes(1));

    const workAuth = await screen.findByTestId("field-input-workAuthorization");
    expect(workAuth).toHaveValue("");
    expect(screen.getByTestId("field-input-noticePeriod")).toHaveValue("");
    expect(screen.getByTestId("field-input-salaryExpectation")).toHaveValue("");
    expect(screen.getByTestId("field-input-currentLocation")).toHaveValue("");
    expect(screen.getByTestId("field-input-linkedinUrl")).toHaveValue("");
    expect(screen.getByTestId("field-input-githubUrl")).toHaveValue("");
    expect(screen.getByTestId("field-input-portfolioUrl")).toHaveValue("");
    expect(screen.getByTestId("field-input-roomToGrow")).toHaveValue("");

    expect(screen.queryByTestId("apply-profile-last-saved")).not.toBeInTheDocument();
    expect(screen.queryByTestId("apply-profile-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("apply-profile-save-error")).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FE-C2: loads and pre-fills a previously saved profile (AC2, AC4)
// ---------------------------------------------------------------------------

describe("FE-C2: loads and pre-fills a previously saved profile (AC2, AC4)", () => {
  it("pre-fills every field including booleans, languages and last-saved indicator", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    expect(await screen.findByTestId("field-input-workAuthorization")).toHaveValue("US Citizen");
    expect(screen.getByTestId("field-input-noticePeriod")).toHaveValue("2 weeks");
    expect(screen.getByTestId("field-input-salaryExpectation")).toHaveValue("$120k-$140k");
    expect(screen.getByTestId("field-input-currentLocation")).toHaveValue("Madrid, Spain");
    expect(screen.getByTestId("field-input-linkedinUrl")).toHaveValue("https://linkedin.com/in/alice");
    expect(screen.getByTestId("field-input-githubUrl")).toHaveValue("https://github.com/alice");
    expect(screen.getByTestId("field-input-portfolioUrl")).toHaveValue("https://alice.dev");
    expect(screen.getByTestId("field-input-roomToGrow")).toHaveValue("Grow into a staff engineer role");

    expect(screen.getByTestId("bool-requiresSponsorship-no")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("bool-willingToRelocate-yes")).toHaveAttribute("aria-pressed", "true");

    expect(screen.getByTestId("language-input-0")).toHaveValue("English (native)");
    expect(screen.getByTestId("language-input-1")).toHaveValue("Spanish (C1)");

    expect(screen.getByTestId("apply-profile-last-saved")).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FE-C3: save a full profile calls PUT with the right body (AC2)
// ---------------------------------------------------------------------------

describe("FE-C3: save a full profile calls PUT with the right body (AC2)", () => {
  it("calls saveApplyProfile once with every current on-screen value", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockResolvedValue(FULL_PROFILE);

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");

    await user.type(screen.getByTestId("field-input-workAuthorization"), "US Citizen");
    await user.type(screen.getByTestId("field-input-noticePeriod"), "2 weeks");
    await user.type(screen.getByTestId("field-input-salaryExpectation"), "$120k-$140k");
    await user.type(screen.getByTestId("field-input-currentLocation"), "Madrid, Spain");
    await user.type(screen.getByTestId("field-input-linkedinUrl"), "https://linkedin.com/in/alice");
    await user.type(screen.getByTestId("field-input-githubUrl"), "https://github.com/alice");
    await user.type(screen.getByTestId("field-input-portfolioUrl"), "https://alice.dev");
    await user.type(screen.getByTestId("field-input-roomToGrow"), "Grow into a staff engineer role");

    await user.click(screen.getByTestId("bool-requiresSponsorship-no"));
    await user.click(screen.getByTestId("bool-willingToRelocate-yes"));

    await user.click(screen.getByTestId("language-add"));
    await user.type(screen.getByTestId("language-input-0"), "English (native)");
    await user.click(screen.getByTestId("language-add"));
    await user.type(screen.getByTestId("language-input-1"), "Spanish (C1)");

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    expect(saveApplyProfile).toHaveBeenCalledWith({
      workAuthorization: "US Citizen",
      requiresSponsorship: false,
      noticePeriod: "2 weeks",
      salaryExpectation: "$120k-$140k",
      currentLocation: "Madrid, Spain",
      willingToRelocate: true,
      linkedinUrl: "https://linkedin.com/in/alice",
      githubUrl: "https://github.com/alice",
      portfolioUrl: "https://alice.dev",
      languages: ["English (native)", "Spanish (C1)"],
      roomToGrow: "Grow into a staff engineer role",
    });

    await waitFor(() => expect(screen.getByTestId("apply-profile-last-saved")).toBeInTheDocument());
  });
});

// ---------------------------------------------------------------------------
// FE-C4: save a partial profile from never-saved (AC3)
// ---------------------------------------------------------------------------

describe("FE-C4: save a partial profile from never-saved (AC3)", () => {
  it("saves only the touched fields; the rest are null in the PUT body", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockResolvedValue({
      ...EMPTY_PROFILE,
      workAuthorization: "US Citizen",
      currentLocation: "Madrid, Spain",
      updatedAt: "2026-07-22T09:00:00Z",
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    await user.type(screen.getByTestId("field-input-workAuthorization"), "US Citizen");
    await user.type(screen.getByTestId("field-input-currentLocation"), "Madrid, Spain");

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    const body = saveApplyProfile.mock.calls[0][0];
    expect(body.workAuthorization).toBe("US Citizen");
    expect(body.currentLocation).toBe("Madrid, Spain");
    expect(body.noticePeriod).toBeNull();
    expect(body.salaryExpectation).toBeNull();
    expect(body.linkedinUrl).toBeNull();
    expect(body.githubUrl).toBeNull();
    expect(body.portfolioUrl).toBeNull();
    expect(body.roomToGrow).toBeNull();
    expect(body.requiresSponsorship).toBeNull();
    expect(body.willingToRelocate).toBeNull();
    expect(body.languages).toBeNull();

    await waitFor(() => expect(screen.getByTestId("field-input-noticePeriod")).toHaveValue(""));
  });
});

// ---------------------------------------------------------------------------
// FE-C5: editing one field resubmits the rest unchanged (AC4)
// ---------------------------------------------------------------------------

describe("FE-C5: editing one field resubmits the rest unchanged (AC4)", () => {
  it("PUT body keeps the original loaded values for every field except the edited one", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    saveApplyProfile.mockResolvedValue({ ...FULL_PROFILE, noticePeriod: "Immediate" });

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-noticePeriod");
    await user.clear(screen.getByTestId("field-input-noticePeriod"));
    await user.type(screen.getByTestId("field-input-noticePeriod"), "Immediate");

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    expect(saveApplyProfile).toHaveBeenCalledWith({
      workAuthorization: FULL_PROFILE.workAuthorization,
      requiresSponsorship: FULL_PROFILE.requiresSponsorship,
      noticePeriod: "Immediate",
      salaryExpectation: FULL_PROFILE.salaryExpectation,
      currentLocation: FULL_PROFILE.currentLocation,
      willingToRelocate: FULL_PROFILE.willingToRelocate,
      linkedinUrl: FULL_PROFILE.linkedinUrl,
      githubUrl: FULL_PROFILE.githubUrl,
      portfolioUrl: FULL_PROFILE.portfolioUrl,
      languages: FULL_PROFILE.languages,
      roomToGrow: FULL_PROFILE.roomToGrow,
    });
  });
});

// ---------------------------------------------------------------------------
// FE-C6: clearing one field sends null for it (AC5)
// ---------------------------------------------------------------------------

describe("FE-C6: clearing one field sends null for it (AC5)", () => {
  it("blanking salaryExpectation and saving sends null for it, others unaffected", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    saveApplyProfile.mockResolvedValue({ ...FULL_PROFILE, salaryExpectation: null });

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-salaryExpectation");
    await user.clear(screen.getByTestId("field-input-salaryExpectation"));

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    const body = saveApplyProfile.mock.calls[0][0];
    expect(body.salaryExpectation).toBeNull();
    expect(body.workAuthorization).toBe(FULL_PROFILE.workAuthorization);
    expect(body.currentLocation).toBe(FULL_PROFILE.currentLocation);

    await waitFor(() => expect(screen.getByTestId("field-input-salaryExpectation")).toHaveValue(""));
    expect(screen.getByTestId("field-input-workAuthorization")).toHaveValue(FULL_PROFILE.workAuthorization);
  });
});

// ---------------------------------------------------------------------------
// FE-C7: clearing every field, "last saved" still shown (AC6)
// ---------------------------------------------------------------------------

describe('FE-C7: clearing every field, "last saved" still shown (AC6)', () => {
  it("PUT body has every field null; response shows empty fields + last-saved indicator", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    saveApplyProfile.mockResolvedValue({ ...EMPTY_PROFILE, updatedAt: "2026-07-22T09:30:00Z" });

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    for (const key of [
      "workAuthorization", "noticePeriod", "salaryExpectation", "currentLocation",
      "linkedinUrl", "githubUrl", "portfolioUrl", "roomToGrow",
    ]) {
      await user.clear(screen.getByTestId(`field-input-${key}`));
    }
    await user.click(screen.getByTestId("bool-requiresSponsorship-unset"));
    await user.click(screen.getByTestId("bool-willingToRelocate-unset"));
    await user.click(screen.getByTestId("language-remove-1"));
    await user.click(screen.getByTestId("language-remove-0"));

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    const body = saveApplyProfile.mock.calls[0][0];
    Object.keys(EMPTY_PROFILE).forEach((key) => {
      if (key === "updatedAt") return;
      expect(body[key]).toBeNull();
    });

    await waitFor(() => expect(screen.getByTestId("apply-profile-last-saved")).toBeInTheDocument());
    expect(screen.getByTestId("field-input-workAuthorization")).toHaveValue("");
  });
});

// ---------------------------------------------------------------------------
// FE-C8: validation surfacing: over-length field (AC7)
// ---------------------------------------------------------------------------

describe("FE-C8: validation surfacing: over-length field (AC7)", () => {
  it("flags roomToGrow with a plain-language error and keeps the unsaved input", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    const longText = "x".repeat(2001);
    saveApplyProfile.mockRejectedValue(
      new ApiError(400, "Validation Failed", {
        error: "Validation Failed",
        message: "roomToGrow must be at most 2000 characters",
      })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    const roomToGrow = await screen.findByTestId("field-input-roomToGrow");
    await user.click(roomToGrow);
    await user.paste(longText);
    await user.type(screen.getByTestId("field-input-workAuthorization"), "US Citizen");

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));

    const field = await screen.findByTestId("field-roomToGrow");
    const alert = within(field).getByRole("alert");
    expect(alert.textContent).toMatch(/room to grow/i);

    // Nothing discarded: the invalid + other in-progress input remain as typed.
    expect(screen.getByTestId("field-input-roomToGrow")).toHaveValue(longText);
    expect(screen.getByTestId("field-input-workAuthorization")).toHaveValue("US Citizen");
  });
});

// ---------------------------------------------------------------------------
// FE-C9: validation surfacing: too many languages (AC8)
// ---------------------------------------------------------------------------

describe("FE-C9: validation surfacing: too many languages (AC8)", () => {
  it("flags languages with an error; all 21 entries remain visible", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockRejectedValue(
      new ApiError(400, "Validation Failed", {
        error: "Validation Failed",
        message: "languages must contain at most 20 entries",
      })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("language-add");
    for (let i = 0; i < 21; i++) {
      await user.click(screen.getByTestId("language-add"));
      await user.type(screen.getByTestId(`language-input-${i}`), `Lang${i}`);
    }

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));

    const field = await screen.findByTestId("field-languages");
    const alert = within(field).getByRole("alert");
    expect(alert.textContent).toMatch(/language/i);

    for (let i = 0; i < 21; i++) {
      expect(screen.getByTestId(`language-input-${i}`)).toHaveValue(`Lang${i}`);
    }
  });
});

// ---------------------------------------------------------------------------
// FE-C10: validation surfacing: malformed URL (AC9)
// ---------------------------------------------------------------------------

describe("FE-C10: validation surfacing: malformed URL (AC9)", () => {
  it("flags linkedinUrl specifically; invalid text remains, rest of form untouched", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockRejectedValue(
      new ApiError(400, "Validation Failed", {
        error: "Validation Failed",
        message: "linkedinUrl must be a valid URL",
      })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-linkedinUrl");
    await user.type(screen.getByTestId("field-input-linkedinUrl"), "not a link");
    await user.type(screen.getByTestId("field-input-currentLocation"), "Madrid, Spain");

    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));

    const field = await screen.findByTestId("field-linkedinUrl");
    const alert = within(field).getByRole("alert");
    expect(alert.textContent).toMatch(/linkedin/i);

    expect(screen.getByTestId("field-input-linkedinUrl")).toHaveValue("not a link");
    expect(screen.getByTestId("field-input-currentLocation")).toHaveValue("Madrid, Spain");
  });
});

// ---------------------------------------------------------------------------
// FE-C11 / FE-C12: copy writes exact value, shows confirmation, never saves,
// never affects other fields (AC10)
// ---------------------------------------------------------------------------

describe("FE-C11: copy writes the exact rendered value + confirmation + no save call (AC10)", () => {
  it("copies the current value, shows a confirmation, and does not call saveApplyProfile", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);

    const user = userEvent.setup();
    const writeText = spyOnClipboard();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    await user.click(screen.getByTestId("field-copy-workAuthorization"));

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith("US Citizen");

    expect(await screen.findByTestId("field-copied-workAuthorization")).toBeInTheDocument();
    expect(saveApplyProfile).not.toHaveBeenCalled();
  });
});

describe("FE-C12: copy on one field never affects another, even with a live validation error elsewhere (AC10)", () => {
  it("copying a field succeeds while another field shows a prior validation error", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    saveApplyProfile.mockRejectedValue(
      new ApiError(400, "Validation Failed", {
        error: "Validation Failed",
        message: "linkedinUrl must be a valid URL",
      })
    );

    const user = userEvent.setup();
    const writeText = spyOnClipboard();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-linkedinUrl");
    await user.clear(screen.getByTestId("field-input-linkedinUrl"));
    await user.type(screen.getByTestId("field-input-linkedinUrl"), "not a link");
    await user.click(screen.getByTestId("apply-profile-save-button"));
    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    await screen.findByTestId("field-linkedinUrl");

    // Copy a different, valid field while linkedinUrl shows its error.
    await user.click(screen.getByTestId("field-copy-currentLocation"));

    expect(writeText).toHaveBeenCalledWith(FULL_PROFILE.currentLocation);
    expect(saveApplyProfile).toHaveBeenCalledTimes(1); // still just the earlier failed attempt

    // No other field shows a copy confirmation.
    expect(screen.queryByTestId("field-copied-workAuthorization")).not.toBeInTheDocument();
    expect(screen.queryByTestId("field-copied-noticePeriod")).not.toBeInTheDocument();
    expect(await screen.findByTestId("field-copied-currentLocation")).toBeInTheDocument();

    // Other fields' rendered values are unchanged.
    expect(screen.getByTestId("field-input-workAuthorization")).toHaveValue(FULL_PROFILE.workAuthorization);
  });
});

// ---------------------------------------------------------------------------
// FE-C13: copy is a no-op for an empty field (AC11)
// ---------------------------------------------------------------------------

describe("FE-C13: copy is a no-op for an empty field (AC11)", () => {
  it("copy control is disabled for an empty field; clicking does nothing", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);

    const user = userEvent.setup();
    const writeText = spyOnClipboard();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    const copyBtn = screen.getByTestId("field-copy-workAuthorization");
    expect(copyBtn).toBeDisabled();

    await user.click(copyBtn);

    expect(writeText).not.toHaveBeenCalled();
    expect(screen.queryByTestId("field-copied-workAuthorization")).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FE-C14: 401 gate on load (AC12)
// ---------------------------------------------------------------------------

describe("FE-C14: 401 gate on load (AC12)", () => {
  it("invokes onLogout and never renders stale/populated field values", async () => {
    getApplyProfile.mockRejectedValue(new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" }));
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoApplyProfile(user);

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));

    expect(screen.queryByTestId("field-input-workAuthorization")).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FE-C15: 401 on save (AC12)
// ---------------------------------------------------------------------------

describe("FE-C15: 401 on save (AC12)", () => {
  it("invokes onLogout and does not show a false last-saved indicator", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockRejectedValue(new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" }));
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    await user.type(screen.getByTestId("field-input-workAuthorization"), "US Citizen");
    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("apply-profile-last-saved")).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FE-C16: cross-section isolation sanity (AC13)
// ---------------------------------------------------------------------------

describe("FE-C16: cross-section isolation sanity (AC13)", () => {
  it("Apply profile section never calls updateCurrentUser (Account/#296 wiring)", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    saveApplyProfile.mockResolvedValue(FULL_PROFILE);

    const user = userEvent.setup();
    renderSettings();
    await gotoApplyProfile(user);

    await screen.findByTestId("field-input-workAuthorization");
    await user.type(screen.getByTestId("field-input-workAuthorization"), "US Citizen");
    await user.click(screen.getByTestId("apply-profile-save-button"));

    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));
    expect(updateCurrentUser).not.toHaveBeenCalled();
  });

  it("editing the Account first-name field never calls getApplyProfile/saveApplyProfile", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);

    const user = userEvent.setup();
    renderSettings();

    // Default section is Account.
    const firstName = screen.getByDisplayValue("Jo");
    await user.clear(firstName);
    await user.type(firstName, "Joanna");

    expect(getApplyProfile).not.toHaveBeenCalled();
    expect(saveApplyProfile).not.toHaveBeenCalled();
  });
});
