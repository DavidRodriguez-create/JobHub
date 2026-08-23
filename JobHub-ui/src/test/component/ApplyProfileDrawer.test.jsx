/**
 * Standalone component tests for ApplyProfileDrawer.
 * Story #460 (sub-issue #481). Cases TC-460-1..14 from
 * the QAE spec on issue #480 (section 3: standalone `ApplyProfileDrawer`).
 *
 * Mocking mirrors SettingsApplyProfile.test.jsx: getApplyProfile/saveApplyProfile
 * mocked on api/auth.js, ApiError mocked on api/client.js, clipboard spied
 * AFTER userEvent.setup().
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/auth.js", async () => {
  const actual = await vi.importActual("../../api/auth.js");
  return {
    ...actual,
    getApplyProfile: vi.fn(),
    saveApplyProfile: vi.fn(),
  };
});

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  },
}));

import { getApplyProfile, saveApplyProfile } from "../../api/auth.js";
import { ApiError } from "../../api/client.js";
import { ApplyProfileDrawer } from "../../components/applyProfile/ApplyProfileDrawer.jsx";
import { clearApplyProfileCache } from "../../components/applyProfile/applyProfileCache.js";

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

const SPARSE_PROFILE = { ...FULL_PROFILE, portfolioUrl: null, roomToGrow: null };

function renderDrawer(props = {}) {
  return render(
    <ApplyProfileDrawer
      authed={true}
      pushToast={vi.fn()}
      onClose={vi.fn()}
      onUpdateInSettings={vi.fn()}
      onLogout={vi.fn()}
      onLogin={vi.fn()}
      {...props}
    />
  );
}

function spyOnClipboard() {
  return vi.spyOn(navigator.clipboard, "writeText");
}

beforeEach(() => {
  vi.clearAllMocks();
  // Story #483: the drawer now seeds from a module-level stale-while-revalidate
  // cache. Clear it between tests so each case starts from a cold cache (the
  // loading-state cases depend on there being nothing cached to render first).
  clearApplyProfileCache();
});

describe("TC-460-1: loading state (AC-460-14)", () => {
  it("shows a loading indicator and no field rows/empty-state/copy controls", async () => {
    getApplyProfile.mockReturnValue(new Promise(() => {}));

    renderDrawer();

    expect(await screen.findByTestId("apply-profile-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("apply-profile-field-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("apply-profile-empty")).not.toBeInTheDocument();
    expect(screen.queryByTestId(/^field-copy-/)).not.toBeInTheDocument();
  });
});

describe("TC-460-2: fields render in the defined order (AC-460-7)", () => {
  it("renders all 11 field rows in the exact §3 order", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);

    renderDrawer();

    const rows = await screen.findAllByTestId("apply-profile-field-row");
    expect(rows.map((r) => r.textContent)).toEqual([
      expect.stringContaining("Work authorization"),
      expect.stringContaining("Notice period"),
      expect.stringContaining("Salary expectation"),
      expect.stringContaining("Current location"),
      expect.stringContaining("Requires sponsorship"),
      expect.stringContaining("Willing to relocate"),
      expect.stringContaining("LinkedIn URL"),
      expect.stringContaining("GitHub URL"),
      expect.stringContaining("Portfolio URL"),
      expect.stringContaining("Languages"),
      expect.stringContaining("Room to grow"),
    ]);
    expect(rows).toHaveLength(11);
  });
});

describe("TC-460-3: unset fields omitted, others normal (AC-460-12)", () => {
  it("omits portfolioUrl/roomToGrow rows, renders the other 9 with copy controls", async () => {
    getApplyProfile.mockResolvedValue(SPARSE_PROFILE);

    renderDrawer();

    await screen.findByTestId("field-copy-workAuthorization");
    expect(screen.queryByTestId("field-copy-portfolioUrl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("field-copy-roomToGrow")).not.toBeInTheDocument();
    expect(screen.queryByText("Not set")).not.toBeInTheDocument();

    const rows = screen.getAllByTestId("apply-profile-field-row");
    expect(rows).toHaveLength(9);
    [
      "workAuthorization", "noticePeriod", "salaryExpectation", "currentLocation",
      "requiresSponsorship", "willingToRelocate", "linkedinUrl", "githubUrl", "languages",
    ].forEach((key) => {
      expect(screen.getByTestId(`field-copy-${key}`)).toBeInTheDocument();
    });
  });
});

describe("TC-460-4: all-empty profile shows the empty state (AC-460-13)", () => {
  it("shows apply-profile-empty with its CTA and no field-copy controls", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);

    renderDrawer();

    expect(await screen.findByTestId("apply-profile-empty")).toBeInTheDocument();
    expect(screen.queryByTestId(/^field-copy-/)).not.toBeInTheDocument();
    expect(screen.getByTestId("apply-profile-empty-cta")).toBeInTheDocument();
  });
});

describe("TC-460-5: copying a text field (AC-460-8)", () => {
  it("writes the exact value, toasts, shows confirmation, never saves", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const writeText = spyOnClipboard();
    const pushToast = vi.fn();

    renderDrawer({ pushToast });
    await screen.findByTestId("field-copy-noticePeriod");
    await user.click(screen.getByTestId("field-copy-noticePeriod"));

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith("2 weeks");
    expect(pushToast).toHaveBeenCalledWith("Copied to clipboard.", "copy");
    expect(await screen.findByTestId("field-copied-noticePeriod")).toBeInTheDocument();
    expect(saveApplyProfile).not.toHaveBeenCalled();
  });
});

describe("TC-460-6: copying a boolean field copies Yes text (AC-460-9)", () => {
  it("writes 'Yes' for requiresSponsorship=true", async () => {
    getApplyProfile.mockResolvedValue({ ...FULL_PROFILE, requiresSponsorship: true });
    const user = userEvent.setup();
    const writeText = spyOnClipboard();

    renderDrawer();
    await screen.findByTestId("field-copy-requiresSponsorship");
    await user.click(screen.getByTestId("field-copy-requiresSponsorship"));

    expect(writeText).toHaveBeenCalledWith("Yes");
  });
});

describe("TC-460-6b: copying the boolean's No branch (edge, complements AC-460-9)", () => {
  it("writes 'No' for requiresSponsorship=false", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const writeText = spyOnClipboard();

    renderDrawer();
    await screen.findByTestId("field-copy-requiresSponsorship");
    await user.click(screen.getByTestId("field-copy-requiresSponsorship"));

    expect(writeText).toHaveBeenCalledWith("No");
  });
});

describe("TC-460-7: copying Languages copies the joined list (AC-460-10)", () => {
  it("writes the joined languages string as a single value", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const writeText = spyOnClipboard();

    renderDrawer();
    await screen.findByTestId("field-copy-languages");
    await user.click(screen.getByTestId("field-copy-languages"));

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith("English (native), Spanish (C1)");
  });
});

describe("TC-460-8: copy confirmation is per-field, never simultaneous (AC-460-11)", () => {
  it("moves the 'Copied' confirmation from field A to field B", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    spyOnClipboard();

    renderDrawer();
    await screen.findByTestId("field-copy-workAuthorization");

    await user.click(screen.getByTestId("field-copy-workAuthorization"));
    expect(await screen.findByTestId("field-copied-workAuthorization")).toBeInTheDocument();
    expect(screen.queryByTestId("field-copied-noticePeriod")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("field-copy-noticePeriod"));
    expect(await screen.findByTestId("field-copied-noticePeriod")).toBeInTheDocument();
    expect(screen.queryByTestId("field-copied-workAuthorization")).not.toBeInTheDocument();
  });
});

describe("TC-460-9: load error (non-401) (AC-460-15)", () => {
  it("shows apply-profile-error and keeps Update in settings clickable", async () => {
    getApplyProfile.mockRejectedValue(new ApiError(500, "Server error"));

    renderDrawer();

    const err = await screen.findByTestId("apply-profile-error");
    expect(err.textContent).toMatch(/Couldn't load your apply profile\. Please try again later\./);
    expect(screen.queryByTestId(/^field-copy-/)).not.toBeInTheDocument();
    expect(screen.getByTestId("apply-profile-update-settings")).not.toBeDisabled();
  });
});

describe("TC-460-10: read-only guarantee (AC-460-21)", () => {
  it("never calls saveApplyProfile and renders no editable control", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    spyOnClipboard();
    const onUpdateInSettings = vi.fn();

    const { container } = renderDrawer({ onUpdateInSettings });
    await screen.findByTestId("field-copy-workAuthorization");

    await user.click(screen.getByTestId("field-copy-workAuthorization"));
    await user.click(screen.getByTestId("field-copy-noticePeriod"));
    await user.click(screen.getByTestId("apply-profile-update-settings"));

    expect(saveApplyProfile).not.toHaveBeenCalled();
    expect(onUpdateInSettings).toHaveBeenCalledTimes(1);
    expect(container.querySelectorAll("input, textarea, button[aria-pressed]").length).toBe(0);
  });
});

describe("TC-460-11: 'Update in settings' is present regardless of fetch outcome (edge, §4.8/BR-7)", () => {
  it("is present and enabled while loading", async () => {
    getApplyProfile.mockReturnValue(new Promise(() => {}));
    renderDrawer();
    const btn = await screen.findByTestId("apply-profile-update-settings");
    expect(btn).not.toBeDisabled();
  });

  it("is present and enabled once populated", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    renderDrawer();
    const btn = await screen.findByTestId("apply-profile-update-settings");
    expect(btn).not.toBeDisabled();
  });

  it("is present and enabled in the all-empty state", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    renderDrawer();
    const btn = await screen.findByTestId("apply-profile-update-settings");
    expect(btn).not.toBeDisabled();
  });

  it("is present and enabled on a non-401 load error", async () => {
    getApplyProfile.mockRejectedValue(new ApiError(500, "Server error"));
    renderDrawer();
    const btn = await screen.findByTestId("apply-profile-update-settings");
    expect(btn).not.toBeDisabled();
  });
});

describe("TC-460-12: backdrop click closes (AC-460-3, standalone half)", () => {
  it("calls onClose exactly once", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const onClose = vi.fn();

    const { container } = renderDrawer({ onClose });
    await screen.findByTestId("field-copy-workAuthorization");
    await user.click(container.querySelector(".apply-drawer-backdrop"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("TC-460-13: Esc closes, no stacking (AC-460-4)", () => {
  it("calls onClose exactly once on a single Escape", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderDrawer({ onClose });
    await screen.findByTestId("field-copy-workAuthorization");
    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("TC-460-14: close (x) button closes (AC-460-6)", () => {
  it("calls onClose exactly once", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderDrawer({ onClose });
    await screen.findByTestId("field-copy-workAuthorization");
    await user.click(screen.getByLabelText("Close"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
