/**
 * App-level wiring tests for the apply-profile quick-access drawer.
 * Story #460 (sub-issue #481). Cases TC-460-15..26 from the QAE spec on
 * issue #480 (section 4: App-level wiring).
 *
 * Mocking mirrors CommandPaletteSettingsNav.test.jsx: api/*, data/mockData.js is
 * left REAL (JobSearchScreen upserts fetched jobs into it, JobDetailDrawer reads
 * company info via DATA.coOf), and Icon.jsx is mocked for speed. screens/JobSearch.jsx
 * and screens/SavedSettings.jsx are NOT mocked (this is what we're proving); screens/
 * Applications.jsx and screens/Dashboard.jsx are mocked to minimal stubs.
 */
import React from "react";
import { render, screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/auth.js", () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  requestVerification: vi.fn(),
  changePassword: vi.fn(),
  setupTwoFactor: vi.fn(),
  verifyTwoFactorSetup: vi.fn(),
  disableTwoFactor: vi.fn(),
  getApplyProfile: vi.fn(),
  saveApplyProfile: vi.fn(),
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

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn(),
  listSavedJobs: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  saveJob: vi.fn(),
  unsaveJob: vi.fn(),
  getJob: vi.fn(),
  peekSearch: vi.fn(() => undefined),
  prefetchSearch: vi.fn(),
  getJobFacets: vi.fn().mockResolvedValue({
    companies: [], locations: [], languages: [], employmentTypes: [],
    careerLevels: [], compensationMin: 0, compensationMax: 300000,
  }),
  listSavedFilters: vi.fn().mockResolvedValue([]),
  getAdminTriggerStatus: vi.fn().mockResolvedValue({
    triggerEnabled: true, codeRequired: false, crawl: null, enrichment: null,
  }),
  triggerAdminPass: vi.fn(),
}));

vi.mock("../../api/applications.js", () => ({
  listApplications: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  applicationStats: vi.fn().mockResolvedValue(null),
  getApplication: vi.fn(),
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  updateApplicationJob: vi.fn(),
  updateApplicationStatus: vi.fn(),
  deleteApplication: vi.fn(),
}));

vi.mock("../../api/notifications.js", () => ({
  // Never resolves: keeps NotificationsSection's prefetch inert for these tests.
  getNotificationPreferences: vi.fn(() => new Promise(() => {})),
  updateNotificationPreferences: vi.fn(),
  getUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
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

vi.mock("../../screens/Applications.jsx", () => ({
  ApplicationsScreen: () => <div data-testid="screen-applications">Applications</div>,
  ApplicationDetailScreen: () => null,
}));

vi.mock("../../screens/Dashboard.jsx", () => ({
  DashboardScreen: () => <div data-testid="screen-dashboard">Dashboard</div>,
}));

// screens/JobSearch.jsx and screens/SavedSettings.jsx are NOT mocked: these tests
// prove the real trigger wiring (Job Search topbar + Job Detail drawer foot) and the
// real "Update in settings" landing on the real Settings screen.

import * as authApi from "../../api/auth.js";
import { getToken } from "../../api/client.js";
import { searchJobs, getJob } from "../../api/jobs.js";
import { getApplyProfile, saveApplyProfile } from "../../api/auth.js";
import { clearApplyProfileCache } from "../../components/applyProfile/applyProfileCache.js";
import App from "../../App.jsx";

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

// Full-detail dtos (description + requirements present) so jobFromApi marks
// hasFullDetail=true and the Job Detail drawer never needs to hydrate via getJob.
const JOB_A_DTO = {
  id: "job-a", title: "Frontend Engineer", location: "Remote",
  employmentType: "FULL_TIME", compensationMin: 100000, compensationMax: 140000,
  firstSeenAt: "2026-07-01T00:00:00Z", source: "Greenhouse", language: ["English"],
  url: "https://example.com/job-a", company: { name: "Acme Corp" },
  description: "A great frontend role.", requirements: ["3+ years"],
};

const JOB_B_DTO = {
  id: "job-b", title: "Backend Engineer", location: "Remote",
  employmentType: "FULL_TIME", compensationMin: 110000, compensationMax: 150000,
  firstSeenAt: "2026-07-02T00:00:00Z", source: "Lever", language: ["English"],
  url: "https://example.com/job-b", company: { name: "Acme Corp" },
  description: "A great backend role.", requirements: ["5+ years"],
};

async function bootAsAuthedUser(account = { isAdmin: false, email: "user@example.com" }) {
  getToken.mockReturnValue("a-valid-jwt");
  authApi.currentUser.mockResolvedValue(account);
  render(<App />);
  await screen.findByTestId("apply-profile-trigger-search");
}

async function bootAsUnauthedUser() {
  getToken.mockReturnValue(null);
  render(<App />);
  // #483 (1): the Apply profile trigger is no longer rendered when signed out,
  // so wait on the public job list instead to know the search screen is ready.
  await screen.findByText("Frontend Engineer");
}

async function openJobDetail(user, title) {
  const titleNode = await screen.findByText(title);
  await user.click(titleNode);
  await screen.findByText(title, { selector: ".job-drawer *" }).catch(() => {});
  // Wait for the drawer chrome itself.
  await waitFor(() => expect(document.querySelector(".job-drawer")).toBeInTheDocument());
}

function settingsNavLink(label) {
  const nav = document.querySelector(".settings-nav");
  return within(nav).getByText(label, { exact: true });
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  clearApplyProfileCache(); // #483: cold cache per test (SWR cache is module-level)
  searchJobs.mockResolvedValue({ items: [JOB_A_DTO, JOB_B_DTO], total: 2, page: 0, totalPages: 1 });
  getJob.mockResolvedValue(null);
});

describe("AC-460-1 : open from the Job Search screen", () => {
  it("TC-460-15 : slides in, calls getApplyProfile, renders fields in the §3 order", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));

    expect(document.querySelector(".apply-drawer")).toBeInTheDocument();
    await waitFor(() => expect(getApplyProfile).toHaveBeenCalled());

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
  });
});

describe("AC-460-2 : open from inside the Job Detail drawer", () => {
  it("TC-460-16 : the apply drawer opens alongside the still-open Job Detail drawer", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));

    expect(document.querySelector(".apply-drawer")).toBeInTheDocument();
    expect(document.querySelector(".job-drawer")).toBeInTheDocument();
    expect(within(document.querySelector(".job-drawer")).getByText("Frontend Engineer")).toBeInTheDocument();
    expect(await screen.findByTestId("field-copy-noticePeriod")).toBeInTheDocument();
  });
});

describe("AC-460-3 / #483 : close via backdrop click (stacked case)", () => {
  // No matchMedia in jsdom -> handleApplyBackdropClick treats it as the stacked
  // (narrow) layout, where a background click closes only the apply drawer and
  // reveals the job post behind it.
  it("TC-460-17 : backdrop click closes only the apply drawer, Job Detail remains", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    await screen.findByTestId("field-copy-noticePeriod");

    await user.click(document.querySelector(".apply-drawer-backdrop"));

    // #483: the apply drawer plays a brief exit animation before it unmounts.
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    const jobDrawer = document.querySelector(".job-drawer");
    expect(jobDrawer).toBeInTheDocument();
    expect(within(jobDrawer).getByText("Frontend Engineer")).toBeInTheDocument();
  });
});

describe("#483 (2c) : side-by-side background click backtracks BOTH drawers", () => {
  it("wide layout: a background (dark-area) click closes the apply drawer AND the job post", async () => {
    // Simulate a wide viewport so handleApplyBackdropClick takes the side-by-side branch.
    const realMatchMedia = window.matchMedia;
    window.matchMedia = (q) => ({
      matches: /min-width:\s*1280px/.test(q), media: q,
      addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, dispatchEvent() { return false; },
    });
    try {
      getApplyProfile.mockResolvedValue(FULL_PROFILE);
      const user = userEvent.setup();
      await bootAsAuthedUser();

      await openJobDetail(user, "Frontend Engineer");
      await user.click(screen.getByTestId("apply-profile-trigger-detail"));
      await screen.findByTestId("field-copy-noticePeriod");

      await user.click(document.querySelector(".apply-drawer-backdrop"));

      // #483: the apply drawer backtracks out FIRST...
      await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
      // ...while the job post is still present (it exits after the apply drawer)...
      expect(document.querySelector(".job-drawer")).toBeInTheDocument();
      // ...then the job post backtracks out.
      await waitFor(() => expect(document.querySelector(".job-drawer")).not.toBeInTheDocument());
    } finally {
      window.matchMedia = realMatchMedia;
    }
  });
});

describe("BR-9 (edge) : close (x) button independence when stacked", () => {
  it("TC-460-18 : closing the apply drawer's own (x) leaves the Job Detail drawer open", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    await screen.findByTestId("field-copy-noticePeriod");

    const applyDrawer = document.querySelector(".apply-drawer");
    await user.click(within(applyDrawer).getByLabelText("Close"));

    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    const jobDrawer = document.querySelector(".job-drawer");
    expect(jobDrawer).toBeInTheDocument();
    expect(within(jobDrawer).getByText("Frontend Engineer")).toBeInTheDocument();
  });
});

describe("AC-460-5 : close via Esc does not also close a stacked Job Detail drawer", () => {
  it("TC-460-19 : a single Escape closes only the apply drawer; Job Detail stays open", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    await screen.findByTestId("field-copy-noticePeriod");

    fireEvent.keyDown(window, { key: "Escape" });

    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    const jobDrawer = document.querySelector(".job-drawer");
    expect(jobDrawer).toBeInTheDocument();
    expect(within(jobDrawer).getByText("Frontend Engineer")).toBeInTheDocument();
  });
});

describe("AC-460-19 : \"Update in settings\" navigates and closes the drawer", () => {
  it("TC-460-20a : from the populated state, drawer closes and Apply profile section becomes active", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    await user.click(await screen.findByTestId("apply-profile-update-settings"));

    expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument();
    await waitFor(() => expect(settingsNavLink("Apply profile")).toHaveClass("active"));
  });

  it("TC-460-20b : from the all-empty state, drawer closes and Apply profile section becomes active", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    await screen.findByTestId("apply-profile-empty");
    await user.click(screen.getByTestId("apply-profile-update-settings"));

    expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument();
    await waitFor(() => expect(settingsNavLink("Apply profile")).toHaveClass("active"));
  });
});

describe("AC-460-20 : empty-state CTA behaves identically to \"Update in settings\"", () => {
  it("TC-460-21 : the empty-state's own CTA closes the drawer and lands on Apply profile", async () => {
    getApplyProfile.mockResolvedValue(EMPTY_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    await user.click(await screen.findByTestId("apply-profile-empty-cta"));

    expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument();
    await waitFor(() => expect(settingsNavLink("Apply profile")).toHaveClass("active"));
  });
});

describe("AC-460-16 : load error (401) signs the user out and closes the drawer", () => {
  it("TC-460-22 : the sign-out path fires and the apply drawer does not linger open", async () => {
    class TestApiError extends Error {
      constructor(status, message) { super(message); this.status = status; }
    }
    getApplyProfile.mockRejectedValue(new TestApiError(401, "Unauthorized"));
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));

    await waitFor(() => expect(authApi.logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    expect(screen.queryByTestId("apply-profile-error")).not.toBeInTheDocument();
  });
});

// #483 (1): supersedes the old AC-460-17/18 (which asserted a visible-but-gated
// trigger when signed out). The trigger is now hidden entirely for signed-out
// users, so there is no unauth drawer to open from the UI.
describe("#483 (1) : the Apply profile trigger is hidden when signed out", () => {
  it("renders no trigger on the search top bar or in the Job Detail drawer, and never fetches", async () => {
    const user = userEvent.setup();
    await bootAsUnauthedUser();

    expect(screen.queryByTestId("apply-profile-trigger-search")).not.toBeInTheDocument();

    // A signed-out user can still open a job's detail drawer, but its foot shows no trigger.
    await openJobDetail(user, "Frontend Engineer");
    expect(screen.queryByTestId("apply-profile-trigger-detail")).not.toBeInTheDocument();

    // The drawer never mounts and no profile GET is attempted while signed out.
    expect(screen.queryByTestId("apply-profile-unauth")).not.toBeInTheDocument();
    expect(getApplyProfile).not.toHaveBeenCalled();
  });
});

describe("#483 (2) : docks beside the Job Detail drawer, standalone from the search top bar", () => {
  it("adds the docked modifiers only when opened from within the Job Detail drawer", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    // Opened from the search top bar (no job drawer): plain, not docked.
    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    await screen.findByTestId("field-copy-noticePeriod");
    expect(document.querySelector(".apply-drawer")).not.toHaveClass("apply-drawer--docked");
    expect(document.querySelector(".apply-drawer-backdrop")).not.toHaveClass("apply-drawer-backdrop--docked");
    await user.click(screen.getByLabelText("Close"));
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());

    // Opened from inside the Job Detail drawer: docked (both panel and backdrop).
    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    await screen.findByTestId("field-copy-noticePeriod");
    expect(document.querySelector(".apply-drawer")).toHaveClass("apply-drawer--docked");
    expect(document.querySelector(".apply-drawer-backdrop")).toHaveClass("apply-drawer-backdrop--docked");
  });
});

describe("#483 (1b) : the trigger toggles the drawer shut on a second click", () => {
  it("clicking Apply profile again while it is open closes it (Job Detail stays open)", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    const trigger = screen.getByTestId("apply-profile-trigger-detail");
    await user.click(trigger);
    await screen.findByTestId("field-copy-noticePeriod");
    expect(document.querySelector(".apply-drawer")).toBeInTheDocument();

    // Second click on the same trigger closes the apply drawer; the job drawer remains.
    await user.click(trigger);
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    expect(document.querySelector(".job-drawer")).toBeInTheDocument();
  });
});

describe("#483 (2b) : closing the job post also closes the docked apply drawer", () => {
  it("closing the Job Detail drawer takes the attached apply drawer with it", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    await screen.findByTestId("field-copy-noticePeriod");
    expect(document.querySelector(".apply-drawer")).toBeInTheDocument();

    // Close the JOB drawer via its own Close button; both drawers go away, in
    // order: the apply drawer first, then the job post (#483).
    const jobDrawer = document.querySelector(".job-drawer");
    await user.click(within(jobDrawer).getByLabelText("Close"));

    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    await waitFor(() => expect(document.querySelector(".job-drawer")).not.toBeInTheDocument());
  });
});

describe("#483 (3) : reopening renders cached values instantly (no spinner)", () => {
  it("shows no loading state on reopen and renders rows immediately", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    await screen.findByTestId("field-copy-noticePeriod");
    await user.click(screen.getByLabelText("Close"));
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());

    // Reopen: the cache is warm, so the loading indicator never appears.
    await user.click(screen.getByTestId("apply-profile-trigger-search"));
    expect(screen.queryByTestId("apply-profile-loading")).not.toBeInTheDocument();
    expect(await screen.findByTestId("field-copy-noticePeriod")).toBeInTheDocument();
  });
});

describe("AC-460-22 : reflects the latest saved values within the same session", () => {
  it("TC-460-25 : the drawer shows the just-saved value, not the pre-edit one", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    saveApplyProfile.mockResolvedValue({ ...FULL_PROFILE, noticePeriod: "Immediate" });
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await user.click(screen.getByText("Settings", { exact: true }));
    await user.click(settingsNavLink("Apply profile"));

    await screen.findByTestId("field-input-noticePeriod");
    await user.clear(screen.getByTestId("field-input-noticePeriod"));
    await user.type(screen.getByTestId("field-input-noticePeriod"), "Immediate");
    await user.click(screen.getByTestId("apply-profile-save-button"));
    await waitFor(() => expect(saveApplyProfile).toHaveBeenCalledTimes(1));

    // Simulate the backend now reflecting the save on a fresh GET.
    getApplyProfile.mockResolvedValue({ ...FULL_PROFILE, noticePeriod: "Immediate" });

    await user.click(screen.getByTestId("nav-item-search"));
    await user.click(screen.getByTestId("apply-profile-trigger-search"));

    const rows = await screen.findAllByTestId("apply-profile-field-row");
    const noticeRow = rows.find((r) => r.textContent.includes("Notice period"));
    expect(noticeRow.textContent).toContain("Immediate");
    expect(noticeRow.textContent).not.toContain("2 weeks");
  });
});

describe("AC-460-23 : Job Detail entry point is job-independent", () => {
  it("TC-460-26 : the apply-profile content is identical whether opened from Job A or Job B", async () => {
    getApplyProfile.mockResolvedValue(FULL_PROFILE);
    const user = userEvent.setup();
    await bootAsAuthedUser();

    await openJobDetail(user, "Frontend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    const firstRows = (await screen.findAllByTestId("apply-profile-field-row")).map((r) => r.textContent);
    await user.click(document.querySelector(".apply-drawer-backdrop"));
    await waitFor(() => expect(document.querySelector(".apply-drawer")).not.toBeInTheDocument());
    await user.click(screen.getByLabelText("Close"));
    await waitFor(() => expect(document.querySelector(".job-drawer")).not.toBeInTheDocument());

    await openJobDetail(user, "Backend Engineer");
    await user.click(screen.getByTestId("apply-profile-trigger-detail"));
    const secondRows = (await screen.findAllByTestId("apply-profile-field-row")).map((r) => r.textContent);

    expect(secondRows).toEqual(firstRows);
    getApplyProfile.mock.calls.forEach((call) => expect(call).toEqual([]));
  });
});
