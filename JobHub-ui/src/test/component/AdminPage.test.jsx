/**
 * Component + interaction tests for AdminPage.
 * Cases: UI-04..UI-12 (Story #7: admin trigger crawl & enrichment), UI-01..UI-11
 * (Story #58: stop button), TC-1..TC-43 (Story #302: status panel freshness), and
 * TC-384-U1..U11 (Story #384: admin trigger gated by the admin's own 2FA - replaces
 * the old emailed "Request Code" flow, deleted per the PDA/QAE spec section 8).
 *
 * Strategy:
 * - Mock ../../api/jobs.js (getAdminTriggerStatus, triggerAdminPass)
 * - Mock ../../api/auth.js (requestVerification) - still imported/mocked so tests can
 *   assert it is *never* called from AdminPage (story #384 removed its only caller here;
 *   the function itself remains in api/auth.js for delete-account/delete-all-applications)
 * - Render <AdminPage account={{ isAdmin: true, ... }} /> in isolation
 */
import React from "react";
import { render, screen, waitFor, within, act, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/jobs.js", () => ({
  getAdminTriggerStatus: vi.fn(),
  triggerAdminPass: vi.fn(),
  cancelAdminTrigger: vi.fn(),
}));

vi.mock("../../api/auth.js", () => ({
  requestVerification: vi.fn(),
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

import { getAdminTriggerStatus, triggerAdminPass, cancelAdminTrigger } from "../../api/jobs.js";
import { requestVerification } from "../../api/auth.js";
import { ApiError } from "../../api/client.js";
import { AdminPage, TOAST_DISMISS_MS } from "../../screens/AdminPage.jsx";

const ACCOUNT = { isAdmin: true, email: "admin@example.com" };

const STATUS_NO_CODE_ENABLED = {
  triggerEnabled: true,
  twoFactorRequired: false,
  crawl: null,
  enrichment: null,
};

const STATUS_DISABLED = {
  triggerEnabled: false,
  twoFactorRequired: false,
  crawl: null,
  enrichment: null,
};

const STATUS_CODE_REQUIRED = {
  triggerEnabled: true,
  twoFactorRequired: true,
  crawl: null,
  enrichment: null,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("UI-04: trigger buttons disabled/hidden when triggerEnabled=false", () => {
  it("shows a disabled message and no active trigger buttons; no POST is made", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_DISABLED);

    render(<AdminPage account={ACCOUNT} />);

    await waitFor(() => expect(getAdminTriggerStatus).toHaveBeenCalledTimes(1));
    await screen.findByTestId("trigger-status-panel");

    // No active "Trigger Crawl" / "Trigger Enrichment" buttons
    expect(screen.queryByTestId("trigger-btn-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("trigger-btn-enrichment")).not.toBeInTheDocument();

    // A message indicating triggering is disabled is visible
    expect(screen.getByTestId("triggering-disabled-banner")).toHaveTextContent(/disabled/i);
    expect(screen.getAllByTestId("trigger-disabled-message")[0]).toHaveTextContent(/disabled/i);

    // No trigger POST was made
    expect(triggerAdminPass).not.toHaveBeenCalled();
  });
});

describe("UI-05: trigger buttons active when triggerEnabled=true and twoFactorRequired=false", () => {
  it("shows enabled Trigger Crawl/Enrichment buttons and no code-entry UI", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    const enrichBtn = await screen.findByTestId("trigger-btn-enrichment");

    expect(crawlBtn).not.toBeDisabled();
    expect(enrichBtn).not.toBeDisabled();

    // No code-entry UI visible anywhere
    expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("code-input-enrichment")).not.toBeInTheDocument();
    expect(screen.queryByTestId("request-code-btn-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("request-code-btn-enrichment")).not.toBeInTheDocument();
  });
});

describe("TC-384-U1: code input shown directly when twoFactorRequired=true, no request step", () => {
  it("shows a code input next to each trigger action with no request-code/spinner testids, and never calls requestVerification", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);

    render(<AdminPage account={ACCOUNT} />);

    await screen.findByTestId("trigger-status-panel");

    // A code input is shown directly, no click needed to reveal it (AC-01/AC-27)
    expect(screen.getByTestId("code-input-crawl")).toBeInTheDocument();
    expect(screen.getByTestId("code-input-enrichment")).toBeInTheDocument();
    expect(screen.getByTestId("submit-code-btn-crawl")).toBeInTheDocument();
    expect(screen.getByTestId("submit-code-btn-enrichment")).toBeInTheDocument();

    // The direct trigger button (no-2FA path) is not rendered
    expect(screen.queryByTestId("trigger-btn-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("trigger-btn-enrichment")).not.toBeInTheDocument();

    // The old emailed request-code flow no longer exists anywhere in the DOM
    expect(screen.queryByTestId("request-code-btn-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("request-code-btn-enrichment")).not.toBeInTheDocument();
    expect(screen.queryByTestId("requesting-spinner-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("requesting-spinner-enrichment")).not.toBeInTheDocument();

    // TC-384-U10: requestVerification is never called for this flow
    expect(requestVerification).not.toHaveBeenCalled();
  });
});

describe("UI-07: clicking Trigger Crawl without code gate calls POST /jobs/admin/triggers directly", () => {
  it("calls triggerAdminPass({ kind: 'crawl' }) once with no verification call, and shows a queued state", async () => {
    const queuedStatus = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "queued", requestedAt: "2026-06-06T10:00:00Z" },
    };
    getAdminTriggerStatus
      .mockResolvedValueOnce(STATUS_NO_CODE_ENABLED)
      .mockResolvedValueOnce(queuedStatus)
      .mockResolvedValue(queuedStatus);
    triggerAdminPass.mockResolvedValue({
      id: "11111111-1111-1111-1111-111111111111",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledTimes(1));
    expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl" });
    expect(requestVerification).not.toHaveBeenCalled();

    // UI shows a success/queued state
    await waitFor(() => {
      expect(screen.getByTestId("trigger-success-crawl")).toHaveTextContent(/queued/i);
    });
  });
});

describe("UI-09: status panel shows 'never run' for both kinds on fresh deployment", () => {
  it("shows idle/never-run state for crawl and enrichment, with enabled trigger buttons", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    await screen.findByTestId("trigger-status-panel");

    const crawlPanel = screen.getByTestId("kind-panel-crawl");
    const enrichPanel = screen.getByTestId("kind-panel-enrichment");

    expect(within(crawlPanel).getByTestId("run-info-never")).toHaveTextContent(/never run/i);
    expect(within(enrichPanel).getByTestId("run-info-never")).toHaveTextContent(/never run/i);

    expect(screen.getByTestId("trigger-btn-crawl")).not.toBeDisabled();
    expect(screen.getByTestId("trigger-btn-enrichment")).not.toBeDisabled();
  });
});

describe("UI-10: status panel shows in-progress indicator when kind is 'running'", () => {
  it("shows a running indicator for crawl while enrichment shows its own (idle) state", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "running", requestedAt: "2026-06-06T10:00:00Z", startedAt: "2026-06-06T10:00:05Z", finishedAt: null },
      enrichment: null,
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).getByTestId("running-indicator")).toBeInTheDocument();
    expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/running/i);

    const enrichPanel = screen.getByTestId("kind-panel-enrichment");
    expect(within(enrichPanel).getByTestId("run-info-never")).toHaveTextContent(/never run/i);
  });
});

describe("UI-11: status panel shows succeeded outcome with resultSummary and finishedAt", () => {
  it("shows 'succeeded' status, the result summary, and a relative finishedAt with the absolute value in title (story #302)", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-06T10:05:00Z"));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "succeeded",
          requestedAt: "2026-06-06T09:55:00Z",
          startedAt: "2026-06-06T09:56:00Z",
          finishedAt: "2026-06-06T10:00:00Z",
          resultSummary: "crawled 5 targets, 20 new postings",
          errorReason: null,
        },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlPanel = screen.getByTestId("kind-panel-crawl");
      expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/succeeded/i);
      expect(within(crawlPanel).getByTestId("run-result-summary")).toHaveTextContent(
        "crawled 5 targets, 20 new postings"
      );
      // finishedAt is rendered as relative time (story #302, BR-7), with the
      // absolute human-readable value available via the `title` attribute.
      const finishedEl = within(crawlPanel).getByTestId("run-finished-at");
      expect(finishedEl.textContent).not.toContain("2026-06-06T10:00:00Z");
      expect(finishedEl).toHaveTextContent("5 min ago");
      expect(finishedEl.getAttribute("title")).toMatch(/2026/);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("UI-12: status panel shows failed outcome with errorReason", () => {
  it("shows 'failed' status and the error reason for enrichment", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      enrichment: {
        status: "failed",
        requestedAt: "2026-06-06T08:55:00Z",
        startedAt: "2026-06-06T08:56:00Z",
        finishedAt: "2026-06-06T09:00:00Z",
        resultSummary: null,
        errorReason: "model timeout",
      },
    });

    render(<AdminPage account={ACCOUNT} />);

    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");
    expect(within(enrichPanel).getByTestId("run-status")).toHaveTextContent(/failed/i);
    expect(within(enrichPanel).getByTestId("run-error-reason")).toHaveTextContent("model timeout");
  });
});

describe("TC-384-U11: 429 response from the trigger call itself is surfaced to the user", () => {
  it("shows a 'too many requests' message inline and does not auto-retry", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);
    triggerAdminPass.mockRejectedValue(
      new ApiError(429, "Too Many Requests", { error: "Too Many Requests", message: "rate limited" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const codeInput = await screen.findByTestId("code-input-crawl");
    await user.type(codeInput, "123456");

    const submitBtn = await screen.findByTestId("submit-code-btn-crawl");
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId("code-error-crawl")).toHaveTextContent(/too many requests/i);
    });

    // No automatic retry: exactly one call was made
    expect(triggerAdminPass).toHaveBeenCalledTimes(1);
    expect(requestVerification).not.toHaveBeenCalled();
  });
});

describe("TC-384-U6: 422 response from the trigger call is surfaced inline, and the admin can retry immediately", () => {
  it("shows a verification-related alert, keeps the panel usable, and allows an immediate retry with no request step", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);
    triggerAdminPass
      .mockRejectedValueOnce(
        new ApiError(422, "code invalid", { error: "Verification Required", message: "code invalid" })
      )
      .mockResolvedValueOnce({
        id: "66666666-6666-6666-6666-666666666666",
        kind: "crawl",
        status: "queued",
        requestedAt: "2026-06-06T10:00:00Z",
      });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const codeInput = await screen.findByTestId("code-input-crawl");
    await user.type(codeInput, "000000");

    const submitBtn = await screen.findByTestId("submit-code-btn-crawl");
    await user.click(submitBtn);

    const alertEl = await screen.findByTestId("code-error-crawl");
    expect(alertEl).toHaveAttribute("role", "alert");
    expect(alertEl).toHaveTextContent(/verification|code invalid/i);

    // The page and code input remain rendered and usable
    expect(screen.getByTestId("admin-page")).toBeInTheDocument();
    expect(screen.getByTestId("trigger-status-panel")).toBeInTheDocument();
    expect(screen.getByTestId("code-input-crawl")).toBeInTheDocument();

    // Retry immediately: no separate request step or testid to click first
    expect(screen.queryByTestId("request-code-btn-crawl")).not.toBeInTheDocument();
    await user.clear(codeInput);
    await user.type(codeInput, "123456");
    await user.click(screen.getByTestId("submit-code-btn-crawl"));

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledTimes(2));
    expect(requestVerification).not.toHaveBeenCalled();
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #384 (sub-issue #390): admin trigger gated by the admin's own 2FA.
 * Cases: TC-384-U2..U5, U7..U9 (U1 = above, U6/U11 = rewritten UI-14/UI-13).
 * ───────────────────────────────────────────────────────────────────────── */

describe("TC-384-U2: single enabled trigger button and no code affordance at all when twoFactorRequired=false", () => {
  it("shows one enabled trigger button per kind and no code-related testid whatsoever", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    expect(crawlBtn).not.toBeDisabled();
    expect(screen.getByTestId("trigger-btn-enrichment")).not.toBeDisabled();

    for (const kind of ["crawl", "enrichment"]) {
      expect(screen.queryByTestId(`code-input-${kind}`)).not.toBeInTheDocument();
      expect(screen.queryByTestId(`submit-code-btn-${kind}`)).not.toBeInTheDocument();
      expect(screen.queryByTestId(`code-error-${kind}`)).not.toBeInTheDocument();
      expect(screen.queryByTestId(`request-code-btn-${kind}`)).not.toBeInTheDocument();
      expect(screen.queryByTestId(`requesting-spinner-${kind}`)).not.toBeInTheDocument();
    }
  });
});

describe("TC-384-U3: a 6-digit TOTP code is sent exactly as { kind, code }", () => {
  it("calls triggerAdminPass with exactly { kind: 'crawl', code: '123456' }", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);
    triggerAdminPass.mockResolvedValue({
      id: "77777777-7777-7777-7777-777777777777",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const codeInput = await screen.findByTestId("code-input-crawl");
    await user.type(codeInput, "123456");
    await user.click(screen.getByTestId("submit-code-btn-crawl"));

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledTimes(1));
    expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl", code: "123456" });
    expect(requestVerification).not.toHaveBeenCalled();
  });
});

describe("TC-384-U4: an 8-character non-numeric backup code is accepted and sent as-is", () => {
  it("calls triggerAdminPass with { kind, code: 'AB12CD34' }", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);
    triggerAdminPass.mockResolvedValue({
      id: "88888888-8888-8888-8888-888888888888",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const codeInput = await screen.findByTestId("code-input-crawl");
    await user.type(codeInput, "AB12CD34");
    await user.click(screen.getByTestId("submit-code-btn-crawl"));

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledTimes(1));
    expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl", code: "AB12CD34" });
  });
});

describe("TC-384-U5: an empty code never reaches the API", () => {
  it("never calls triggerAdminPass when the submit affordance is used with an empty code field", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_CODE_REQUIRED);

    render(<AdminPage account={ACCOUNT} />);

    await screen.findByTestId("code-input-crawl");
    const submitBtn = screen.getByTestId("submit-code-btn-crawl");

    expect(submitBtn).toBeDisabled();
    fireEvent.click(submitBtn);

    expect(triggerAdminPass).not.toHaveBeenCalled();
  });
});

describe("TC-384-U7: no-2FA admin fires the trigger with exactly { kind }, no code key present", () => {
  it("calls triggerAdminPass with exactly { kind: 'crawl' } and never calls requestVerification", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockResolvedValue({
      id: "99999999-9999-9999-9999-999999999999",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledTimes(1));
    expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl" });
    expect(requestVerification).not.toHaveBeenCalled();
  });
});

describe("TC-384-U8: no client-side path can ever attach a code for a non-2FA admin", () => {
  it("never renders a code input for this admin, before or after firing the trigger", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockResolvedValue({
      id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();

    await user.click(crawlBtn);

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl" }));
    expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();
  });
});

describe("TC-384-U9: no trace of the old 'Request Code' flow anywhere in the rendered page", () => {
  it.each([
    ["twoFactorRequired=true", STATUS_CODE_REQUIRED],
    ["twoFactorRequired=false", STATUS_NO_CODE_ENABLED],
  ])("renders no request-code-btn-*/requesting-spinner-* testid and no emailed-code copy (%s)", async (_label, status) => {
    getAdminTriggerStatus.mockResolvedValue(status);

    const { container } = render(<AdminPage account={ACCOUNT} />);
    await screen.findByTestId("trigger-status-panel");

    expect(container.querySelector('[data-testid^="request-code-btn-"]')).toBeNull();
    expect(container.querySelector('[data-testid^="requesting-spinner-"]')).toBeNull();
    expect(container.textContent).not.toMatch(/code sent to your email/i);
    expect(container.textContent).not.toMatch(/request code/i);
  });
});

describe("FE-ADM-01: root container uses the .content layout class", () => {
  it("renders the admin-page root with the .content class", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    const root = await screen.findByTestId("admin-page");
    expect(root).toHaveClass("content");
  });
});

describe("FE-ADM-02: page title rendered via Topbar", () => {
  it("renders a .topbar with a .page-title element", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    const { container } = render(<AdminPage account={ACCOUNT} />);

    await screen.findByTestId("trigger-status-panel");

    const topbar = container.querySelector(".topbar");
    expect(topbar).toBeInTheDocument();

    const title = container.querySelector(".topbar .page-title");
    expect(title).toBeInTheDocument();
    expect(title).toHaveTextContent(/admin/i);
  });
});

describe("FE-ADM-03: kind panels use the .card class", () => {
  it("renders kind-panel-crawl and kind-panel-enrichment with the .card class", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");

    expect(crawlPanel).toHaveClass("card");
    expect(enrichPanel).toHaveClass("card");
  });
});

describe("FE-ADM-04: trigger button uses the .btn class", () => {
  it("renders trigger-btn-crawl with the .btn class when triggering is enabled", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    expect(crawlBtn).toHaveClass("btn");
  });
});

describe("FE-ADM-05: disabled banner uses a warning CSS class, not inline style", () => {
  it("renders triggering-disabled-banner with a warning class and no inline background style", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_DISABLED);

    render(<AdminPage account={ACCOUNT} />);

    const banner = await screen.findByTestId("triggering-disabled-banner");
    expect(banner.className).toMatch(/warning/i);
    // Styling should come from a CSS class, not an inline `background` style.
    expect(banner.style.background).toBe("");
  });
});

describe("FE-TST-01: success toast appears after a successful trigger", () => {
  it("shows a success toast/notification with the success message", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockResolvedValue({
      id: "11111111-1111-1111-1111-111111111111",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-success-crawl")).toHaveTextContent(/queued/i);
    });
  });
});

describe("FE-TST-02: success toast auto-dismisses after TOAST_DISMISS_MS", () => {
  it("removes the success toast from the DOM once the dismiss timer elapses", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      triggerAdminPass.mockResolvedValue({
        id: "11111111-1111-1111-1111-111111111111",
        kind: "crawl",
        status: "queued",
        requestedAt: "2026-06-06T10:00:00Z",
      });

      render(<AdminPage account={ACCOUNT} />);

      // Flush the initial fetchStatus effect under fake timers
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlBtn = screen.getByTestId("trigger-btn-crawl");
      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS);
        await Promise.resolve();
      });

      expect(screen.queryByTestId("trigger-success-crawl")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("FE-TST-03: error toast appears when trigger fails with 409", () => {
  it("shows an error toast with the conflict message", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockRejectedValue(
      new ApiError(409, "Conflict", { error: "Conflict", message: "already running" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-crawl")).toHaveTextContent(/already in progress/i);
    });
  });
});

describe("FE-TST-04: error toast auto-dismisses after TOAST_DISMISS_MS", () => {
  it("removes the error toast from the DOM once the dismiss timer elapses", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      triggerAdminPass.mockRejectedValue(
        new ApiError(409, "Conflict", { error: "Conflict", message: "already running" })
      );

      render(<AdminPage account={ACCOUNT} />);

      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlBtn = screen.getByTestId("trigger-btn-crawl");
      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("trigger-error-crawl")).toBeInTheDocument();

      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS);
        await Promise.resolve();
      });

      expect(screen.queryByTestId("trigger-error-crawl")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("FE-TST-05: re-triggering while a success toast is visible refreshes and restarts the timer", () => {
  it("keeps the toast visible after a second trigger and restarts the dismiss timer", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      triggerAdminPass.mockResolvedValue({
        id: "11111111-1111-1111-1111-111111111111",
        kind: "crawl",
        status: "queued",
        requestedAt: "2026-06-06T10:00:00Z",
      });

      render(<AdminPage account={ACCOUNT} />);

      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlBtn = screen.getByTestId("trigger-btn-crawl");

      // First trigger
      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      // Advance most of the way to dismissal, but not all the way
      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS - 1000);
        await Promise.resolve();
      });
      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      // Trigger again: toast should refresh and timer should restart
      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      // Advance by the same amount again: if the timer restarted, toast is still visible
      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS - 1000);
        await Promise.resolve();
      });
      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      // Now advance past the full dismiss window from the second trigger
      await act(async () => {
        vi.advanceTimersByTime(2000);
        await Promise.resolve();
      });
      expect(screen.queryByTestId("trigger-success-crawl")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("FE-TST-06: crawl success toast and enrichment error toast render concurrently with independent timers", () => {
  it("shows both toasts at once and dismisses each independently", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      triggerAdminPass.mockImplementation(({ kind }) => {
        if (kind === "crawl") {
          return Promise.resolve({
            id: "11111111-1111-1111-1111-111111111111",
            kind: "crawl",
            status: "queued",
            requestedAt: "2026-06-06T10:00:00Z",
          });
        }
        return Promise.reject(
          new ApiError(409, "Conflict", { error: "Conflict", message: "already running" })
        );
      });

      render(<AdminPage account={ACCOUNT} />);

      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlBtn = screen.getByTestId("trigger-btn-crawl");
      const enrichBtn = screen.getByTestId("trigger-btn-enrichment");

      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      // Advance partway before triggering enrichment, so timers are offset
      await act(async () => {
        vi.advanceTimersByTime(2000);
        await Promise.resolve();
      });

      await act(async () => {
        fireEvent.click(enrichBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      // Both toasts visible concurrently
      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();
      expect(screen.getByTestId("trigger-error-enrichment")).toBeInTheDocument();

      // Advance to just past the crawl toast's dismiss time (started 2000ms earlier)
      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS - 2000 + 100);
        await Promise.resolve();
      });

      // Crawl toast dismissed, enrichment error toast still visible
      expect(screen.queryByTestId("trigger-success-crawl")).not.toBeInTheDocument();
      expect(screen.getByTestId("trigger-error-enrichment")).toBeInTheDocument();

      // Advance past the enrichment toast's remaining time
      await act(async () => {
        vi.advanceTimersByTime(2000);
        await Promise.resolve();
      });

      expect(screen.queryByTestId("trigger-error-enrichment")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("FE-TST-07: toast cleanup on unmount produces no React warnings", () => {
  it("unmounts before the dismiss timer fires without warning or throwing", async () => {
    vi.useFakeTimers();
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      triggerAdminPass.mockResolvedValue({
        id: "11111111-1111-1111-1111-111111111111",
        kind: "crawl",
        status: "queued",
        requestedAt: "2026-06-06T10:00:00Z",
      });

      const { unmount } = render(<AdminPage account={ACCOUNT} />);

      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlBtn = screen.getByTestId("trigger-btn-crawl");
      await act(async () => {
        fireEvent.click(crawlBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("trigger-success-crawl")).toBeInTheDocument();

      // Unmount well before TOAST_DISMISS_MS elapses
      unmount();

      // Advance timers past the dismiss window: should not throw or warn
      await act(async () => {
        vi.advanceTimersByTime(TOAST_DISMISS_MS + 1000);
        await Promise.resolve();
      });

      const reactWarnings = errorSpy.mock.calls.filter(([msg]) =>
        typeof msg === "string" && /act\(|state update|unmounted component/i.test(msg)
      );
      expect(reactWarnings).toHaveLength(0);
    } finally {
      errorSpy.mockRestore();
      vi.useRealTimers();
    }
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #58: Stop button for admin-triggered crawl/enrichment
 * Cases: UI-01..UI-11
 * ───────────────────────────────────────────────────────────────────────── */

describe("UI-01: Stop button visible when status is 'running' or 'queued'", () => {
  it("shows an enabled stop button for crawl (running) and enrichment (queued)", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "running", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
      enrichment: { status: "queued", requestedAt: "2026-06-12T10:00:00Z" },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");

    const crawlStop = within(crawlPanel).getByTestId("stop-btn-crawl");
    expect(crawlStop).toBeInTheDocument();
    expect(crawlStop).not.toBeDisabled();

    const enrichStop = within(enrichPanel).getByTestId("stop-btn-enrichment");
    expect(enrichStop).toBeInTheDocument();
    expect(enrichStop).not.toBeDisabled();
  });
});

describe("UI-02: Stop button hidden for succeeded, failed, cancelled, cancel_requested, and never-run", () => {
  it.each([
    ["never run", null],
    ["succeeded", { status: "succeeded", requestedAt: "2026-06-12T09:00:00Z", finishedAt: "2026-06-12T09:05:00Z", resultSummary: "ok" }],
    ["failed", { status: "failed", requestedAt: "2026-06-12T09:00:00Z", finishedAt: "2026-06-12T09:05:00Z", errorReason: "boom" }],
    ["cancel_requested", { status: "cancel_requested", requestedAt: "2026-06-12T09:00:00Z", startedAt: "2026-06-12T09:01:00Z", finishedAt: null }],
    ["cancelled", { status: "cancelled", requestedAt: "2026-06-12T09:00:00Z", finishedAt: "2026-06-12T09:05:00Z", resultSummary: "Cancelled after 3 of 10 targets" }],
  ])("hides stop-btn-crawl when status is %s", async (_label, crawlInfo) => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlInfo,
    });

    render(<AdminPage account={ACCOUNT} />);

    await screen.findByTestId("kind-panel-crawl");

    expect(screen.queryByTestId("stop-btn-crawl")).not.toBeInTheDocument();
  });
});

describe("UI-03: Clicking Stop on a 'running' pass calls the cancel API for that kind", () => {
  it("calls cancelAdminTrigger('crawl') once, and not for enrichment", async () => {
    // AC-513-24 regression extension: a populated progress object on the
    // running crawl must not change the Stop behaviour asserted below.
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "running",
        requestedAt: "2026-06-12T10:00:00Z",
        startedAt: "2026-06-12T10:00:05Z",
        finishedAt: null,
        progress: {
          targetsVisited: 3,
          newPosts: 47,
          currentCompany: "Figma",
          currentSourceType: "lever",
          lastCompany: "Klaviyo",
          lastSourceType: "greenhouse",
          lastFoundPosts: 142,
          lastNewPosts: 16,
          updatedAt: "2026-06-12T10:05:00Z",
        },
      },
      enrichment: null,
    });
    cancelAdminTrigger.mockResolvedValue({
      id: "33333333-3333-3333-3333-333333333333",
      kind: "crawl",
      status: "cancel_requested",
      requestedAt: "2026-06-12T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const stopBtn = within(crawlPanel).getByTestId("stop-btn-crawl");
    await user.click(stopBtn);

    await waitFor(() => expect(cancelAdminTrigger).toHaveBeenCalledTimes(1));
    expect(cancelAdminTrigger).toHaveBeenCalledWith("crawl");
  });
});

describe("UI-04: Clicking Stop on a 'queued' pass shows 'Cancelled' immediately and hides Stop", () => {
  it("updates the enrichment panel to Cancelled without waiting for the next poll", async () => {
    const queuedStatus = {
      ...STATUS_NO_CODE_ENABLED,
      enrichment: { status: "queued", requestedAt: "2026-06-12T10:00:00Z" },
    };
    getAdminTriggerStatus.mockResolvedValue(queuedStatus);
    cancelAdminTrigger.mockResolvedValue({
      id: "44444444-4444-4444-4444-444444444444",
      kind: "enrichment",
      status: "cancelled",
      requestedAt: "2026-06-12T10:00:00Z",
      finishedAt: "2026-06-12T10:00:01Z",
      resultSummary: "Cancelled before execution",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");
    const stopBtn = within(enrichPanel).getByTestId("stop-btn-enrichment");
    await user.click(stopBtn);

    await waitFor(() => expect(cancelAdminTrigger).toHaveBeenCalledWith("enrichment"));

    await waitFor(() => {
      expect(within(enrichPanel).getByTestId("run-status")).toHaveTextContent("Cancelled");
    });

    expect(within(enrichPanel).queryByTestId("stop-btn-enrichment")).not.toBeInTheDocument();
  });
});

describe("UI-05: UI shows 'Cancelling…' for cancel_requested", () => {
  it("renders 'Cancelling…' with an in-progress indicator and hides Stop", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "cancel_requested", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");

    expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent("Cancelling…");
    expect(within(crawlPanel).getByTestId("running-indicator")).toBeInTheDocument();
    expect(within(crawlPanel).queryByTestId("stop-btn-crawl")).not.toBeInTheDocument();
  });
});

describe("UI-06: Page continues polling while status is cancel_requested", () => {
  it("fires another getAdminTriggerStatus call after 5s of cancel_requested", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "cancel_requested", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
      });

      render(<AdminPage account={ACCOUNT} />);

      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(getAdminTriggerStatus).toHaveBeenCalledTimes(1);

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(getAdminTriggerStatus.mock.calls.length).toBeGreaterThanOrEqual(2);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("UI-07: UI shows 'Cancelled' for the terminal cancelled state", () => {
  it("renders 'Cancelled' with finishedAt/resultSummary, no running indicator, no Stop button", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-12T10:10:00Z"));
    try {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      enrichment: {
        status: "cancelled",
        requestedAt: "2026-06-12T10:00:00Z",
        startedAt: "2026-06-12T10:00:05Z",
        finishedAt: "2026-06-12T10:05:00Z",
        resultSummary: "Cancelled after 12 of 40 postings",
        errorReason: null,
      },
    });

    render(<AdminPage account={ACCOUNT} />);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const enrichPanel = screen.getByTestId("kind-panel-enrichment");

    const statusEl = within(enrichPanel).getByTestId("run-status");
    expect(statusEl).toHaveTextContent("Cancelled");
    expect(statusEl).not.toHaveTextContent("Cancelling…");

    expect(within(enrichPanel).queryByTestId("running-indicator")).not.toBeInTheDocument();

    // finishedAt is rendered as relative time (story #302, BR-7), with the
    // absolute human-readable value available via the `title` attribute.
    const finishedEl = within(enrichPanel).getByTestId("run-finished-at");
    expect(finishedEl).toBeInTheDocument();
    expect(finishedEl.textContent).not.toContain("2026-06-12T10:05:00Z");
    expect(finishedEl).toHaveTextContent("5 min ago");
    expect(finishedEl.getAttribute("title")).toMatch(/2026/);

    expect(within(enrichPanel).getByTestId("run-result-summary")).toHaveTextContent(
      "Cancelled after 12 of 40 postings"
    );

    expect(within(enrichPanel).queryByTestId("stop-btn-enrichment")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("UI-08: 409 from cancel shows an error toast and refreshes status (E6)", () => {
  it("shows an error toast and reflects the refreshed 'succeeded' status with Stop hidden", async () => {
    const runningStatus = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "running", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
    };
    const succeededStatus = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "succeeded",
        requestedAt: "2026-06-12T10:00:00Z",
        startedAt: "2026-06-12T10:00:05Z",
        finishedAt: "2026-06-12T10:04:00Z",
        resultSummary: "crawled 5 targets",
      },
    };
    getAdminTriggerStatus
      .mockResolvedValueOnce(runningStatus)
      .mockResolvedValue(succeededStatus);
    cancelAdminTrigger.mockRejectedValue(
      new ApiError(409, "Conflict", { error: "No Active Trigger", message: "No active trigger for this kind." })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const stopBtn = within(crawlPanel).getByTestId("stop-btn-crawl");
    await user.click(stopBtn);

    await waitFor(() => expect(cancelAdminTrigger).toHaveBeenCalledWith("crawl"));

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-crawl")).toBeInTheDocument();
    });

    await waitFor(() => expect(getAdminTriggerStatus).toHaveBeenCalledTimes(2));

    await waitFor(() => {
      expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/succeeded/i);
    });
    expect(within(crawlPanel).queryByTestId("stop-btn-crawl")).not.toBeInTheDocument();
  });
});

describe("UI-09: 403 from cancel shows a 'not authorized' error toast (E8)", () => {
  it("shows an error toast mentioning 'not authorized'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "running", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
    });
    cancelAdminTrigger.mockRejectedValue(
      new ApiError(403, "Forbidden", { error: "Forbidden", message: "not allowed" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const stopBtn = within(crawlPanel).getByTestId("stop-btn-crawl");
    await user.click(stopBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-crawl")).toHaveTextContent(/not authorized/i);
    });
  });
});

describe("UI-10: 500 from cancel shows a generic 'Stop failed' error toast (E9)", () => {
  it("shows an error toast mentioning 'stop failed'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      enrichment: { status: "running", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
    });
    cancelAdminTrigger.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");
    const stopBtn = within(enrichPanel).getByTestId("stop-btn-enrichment");
    await user.click(stopBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-enrichment")).toHaveTextContent(/stop failed/i);
    });
  });
});

describe("UI-11: Double-click on Stop disables the button after the first click (E12)", () => {
  it("disables the stop button immediately and only calls cancel once", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "running", requestedAt: "2026-06-12T10:00:00Z", startedAt: "2026-06-12T10:00:05Z", finishedAt: null },
    });

    let resolveCancel;
    cancelAdminTrigger.mockImplementation(
      () => new Promise((resolve) => { resolveCancel = resolve; })
    );

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const stopBtn = within(crawlPanel).getByTestId("stop-btn-crawl");

    await act(async () => {
      fireEvent.click(stopBtn);
      await Promise.resolve();
    });

    expect(within(crawlPanel).getByTestId("stop-btn-crawl")).toBeDisabled();

    // A second click event while still in flight should not fire a second call
    fireEvent.click(within(crawlPanel).getByTestId("stop-btn-crawl"));

    expect(cancelAdminTrigger).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveCancel({
        id: "55555555-5555-5555-5555-555555555555",
        kind: "crawl",
        status: "cancel_requested",
        requestedAt: "2026-06-12T10:00:00Z",
      });
      await Promise.resolve();
      await Promise.resolve();
    });
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #302 (sub-issue #312): admin trigger status panel: in-flight
 * feedback, freshness line, relative timestamps, duration, no-code regression.
 * Cases: TC-1..TC-43
 * ───────────────────────────────────────────────────────────────────────── */

const NOW_ISO = "2026-06-06T10:00:00.000Z";

function isoSecondsBefore(nowIso, seconds) {
  return new Date(new Date(nowIso).getTime() - seconds * 1000).toISOString();
}

describe("TC-1..TC-5: Refresh button in-flight feedback", () => {
  it("TC-1: shows 'Refreshing…' and disables the button immediately on click", async () => {
    getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED);
    let resolvePending;
    getAdminTriggerStatus.mockImplementationOnce(
      () => new Promise((resolve) => { resolvePending = resolve; })
    );

    render(<AdminPage account={ACCOUNT} />);
    const refreshBtn = await screen.findByTestId("refresh-btn");
    expect(refreshBtn).toHaveTextContent("Refresh");
    expect(refreshBtn).not.toBeDisabled();

    fireEvent.click(refreshBtn);

    expect(refreshBtn).toHaveTextContent(/refreshing/i);
    expect(refreshBtn).toBeDisabled();
    expect(getAdminTriggerStatus).toHaveBeenCalledTimes(2);

    await act(async () => {
      resolvePending(STATUS_NO_CODE_ENABLED);
      await Promise.resolve();
    });
  });

  it("TC-2: returns to 'Refresh'/enabled on success and reflects the new payload", async () => {
    getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED);
    let resolvePending;
    getAdminTriggerStatus.mockImplementationOnce(
      () => new Promise((resolve) => { resolvePending = resolve; })
    );

    render(<AdminPage account={ACCOUNT} />);
    const refreshBtn = await screen.findByTestId("refresh-btn");
    fireEvent.click(refreshBtn);
    expect(refreshBtn).toBeDisabled();

    await act(async () => {
      resolvePending({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "succeeded", requestedAt: "2026-06-06T09:55:00Z", finishedAt: "2026-06-06T10:00:00Z", resultSummary: "ok" },
      });
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(refreshBtn).toHaveTextContent("Refresh");
    expect(refreshBtn).not.toBeDisabled();
    await waitFor(() => {
      expect(within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-status")).toHaveTextContent(/succeeded/i);
    });
  });

  it("TC-3: returns to 'Refresh'/enabled on failure, shows load-error, allows re-click", async () => {
    getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED);
    let rejectPending;
    getAdminTriggerStatus.mockImplementationOnce(
      () => new Promise((_resolve, reject) => { rejectPending = reject; })
    );

    render(<AdminPage account={ACCOUNT} />);
    const refreshBtn = await screen.findByTestId("refresh-btn");
    await act(async () => {
      fireEvent.click(refreshBtn);
      await Promise.resolve();
    });
    expect(refreshBtn).toBeDisabled();

    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    await act(async () => {
      rejectPending(new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(refreshBtn).toHaveTextContent("Refresh");
    expect(refreshBtn).not.toBeDisabled();
    expect(screen.getByTestId("load-error")).toBeInTheDocument();

    // A second click is possible immediately (not stuck disabled)
    await act(async () => {
      fireEvent.click(refreshBtn);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(getAdminTriggerStatus).toHaveBeenCalledTimes(3);
  });

  it("TC-4: Refresh button is not required to go busy during an auto-poll-only tick", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "running", requestedAt: "2026-06-06T10:00:00Z", startedAt: "2026-06-06T10:00:05Z", finishedAt: null },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      let resolvePoll;
      getAdminTriggerStatus.mockImplementationOnce(
        () => new Promise((resolve) => { resolvePoll = resolve; })
      );

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
      });

      // Not a positive requirement either way: assert the panel is still usable
      // (freshness/hint cases, TC-7/TC-10/TC-11, are the explicit auto-poll signal).
      expect(screen.getByTestId("admin-page")).toBeInTheDocument();

      await act(async () => {
        resolvePoll({
          ...STATUS_NO_CODE_ENABLED,
          crawl: { status: "running", requestedAt: "2026-06-06T10:00:00Z", startedAt: "2026-06-06T10:00:05Z", finishedAt: null },
        });
        await Promise.resolve();
      });
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-5: a second click while the button is disabled does not fire a second fetch", async () => {
    getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED);
    let resolvePending;
    getAdminTriggerStatus.mockImplementationOnce(
      () => new Promise((resolve) => { resolvePending = resolve; })
    );

    render(<AdminPage account={ACCOUNT} />);
    const refreshBtn = await screen.findByTestId("refresh-btn");
    fireEvent.click(refreshBtn);
    expect(refreshBtn).toBeDisabled();
    expect(getAdminTriggerStatus).toHaveBeenCalledTimes(2);

    fireEvent.click(refreshBtn);
    expect(getAdminTriggerStatus).toHaveBeenCalledTimes(2);

    await act(async () => {
      resolvePending(STATUS_NO_CODE_ENABLED);
      await Promise.resolve();
    });
  });
});

describe("TC-6..TC-13: Freshness line", () => {
  it("TC-6: appears after the first successful load, absent during the loading state", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      let resolveFirst;
      getAdminTriggerStatus.mockImplementationOnce(
        () => new Promise((resolve) => { resolveFirst = resolve; })
      );

      render(<AdminPage account={ACCOUNT} />);
      expect(screen.queryByTestId("freshness-line")).not.toBeInTheDocument();

      await act(async () => {
        resolveFirst(STATUS_NO_CODE_ENABLED);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated (just now|0s ago)/i);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-7: resets to 'just now'/'0s ago' after a successful manual Refresh, then advances again", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      await act(async () => {
        vi.advanceTimersByTime(42000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 42s ago/i);

      const refreshBtn = screen.getByTestId("refresh-btn");
      await act(async () => {
        fireEvent.click(refreshBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated (just now|0s ago)/i);

      await act(async () => {
        vi.advanceTimersByTime(1000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 1s ago/i);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-8: resets after a successful auto-poll tick, exactly as a manual Refresh would", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      // AC-513-22 regression extension: a populated progress object on the
      // payload must not change the freshness-line behaviour below.
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "running",
          requestedAt: "2026-06-06T09:59:00Z",
          startedAt: "2026-06-06T09:59:05Z",
          finishedAt: null,
          progress: {
            targetsVisited: 2,
            newPosts: 30,
            currentCompany: "Klaviyo",
            currentSourceType: "greenhouse",
            lastCompany: null,
            lastSourceType: null,
            lastFoundPosts: null,
            lastNewPosts: null,
            updatedAt: "2026-06-06T09:59:30Z",
          },
        },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      await act(async () => {
        vi.advanceTimersByTime(4000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 4s ago/i);

      // The 5s auto-poll timer fires (1000ms further, reaching the 5000ms interval)
      await act(async () => {
        vi.advanceTimersByTime(1000);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated (just now|0s ago)/i);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-9a: does not reset on a failed manual Refresh, load-error shown alongside", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED);
      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      await act(async () => {
        vi.advanceTimersByTime(30000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 30s ago/i);

      getAdminTriggerStatus.mockRejectedValueOnce(
        new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
      );
      const refreshBtn = screen.getByTestId("refresh-btn");
      await act(async () => {
        fireEvent.click(refreshBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 30s ago/i);
      expect(screen.getByTestId("load-error")).toBeInTheDocument();

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 35s ago/i);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-9b: does not reset on a failed auto-poll tick, keeps polling on its normal cadence", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      const runningStatus = {
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "running", requestedAt: "2026-06-06T09:59:00Z", startedAt: "2026-06-06T09:59:05Z", finishedAt: null },
      };
      // Every poll tick keeps resolving with the same running status, so
      // auto-poll keeps firing every 5s (BR-6) right up until the one tick
      // this case deliberately fails.
      getAdminTriggerStatus.mockResolvedValue(runningStatus);

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      // Advance through 6 poll ticks (30s of 5s cadence), flushing microtasks
      // between each tick so every resolved poll updates lastSuccessAt in turn;
      // the freshness age is only ever measured against the *last* tick, so it
      // reads "0s"/"just now" right after each successful tick, not "30s ago"
      // cumulatively. This case's actual assertion is about the *next* tick,
      // the one that fails, so pin the last successful tick right before it.
      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          vi.advanceTimersByTime(5000);
          await Promise.resolve();
          await Promise.resolve();
        });
      }
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated (just now|0s ago)/i);

      // The next poll tick (E3) fails: freshness must not reset, it keeps
      // advancing from the last successful tick's baseline.
      getAdminTriggerStatus.mockRejectedValueOnce(
        new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
      );
      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 5s ago/i);
      expect(screen.getByTestId("load-error")).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-10: 'just now' for sub-1s age, ticks to '1s ago' then '2s ago'", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated just now/i);

      await act(async () => {
        vi.advanceTimersByTime(1000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 1s ago/i);

      await act(async () => {
        vi.advanceTimersByTime(1000);
      });
      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 2s ago/i);
    } finally {
      vi.useRealTimers();
    }
  });

  it.each(["queued", "running", "cancel_requested"])(
    "TC-11: shows the auto-refreshing hint while crawl.status is '%s'",
    async (statusValue) => {
      // AC-513-23 regression extension: a populated progress object (once the
      // run has actually started reporting) must not change the poll
      // eligibility / hint behaviour below.
      const progress = statusValue === "queued" ? null : {
        targetsVisited: 1,
        newPosts: 10,
        currentCompany: "Klaviyo",
        currentSourceType: "greenhouse",
        lastCompany: null,
        lastSourceType: null,
        lastFoundPosts: null,
        lastNewPosts: null,
        updatedAt: "2026-06-06T09:59:30Z",
      };
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: statusValue, requestedAt: "2026-06-06T09:59:00Z", startedAt: "2026-06-06T09:59:05Z", finishedAt: null, progress },
      });

      render(<AdminPage account={ACCOUNT} />);
      const freshnessLine = await screen.findByTestId("freshness-line");
      expect(freshnessLine.textContent).toMatch(/updated .*auto-refreshing/i);
    }
  );

  it.each([
    ["both succeeded", { status: "succeeded", requestedAt: "2026-06-06T09:00:00Z", finishedAt: "2026-06-06T09:05:00Z", resultSummary: "ok" }, { status: "succeeded", requestedAt: "2026-06-06T09:00:00Z", finishedAt: "2026-06-06T09:05:00Z", resultSummary: "ok" }],
    ["succeeded + never-run", { status: "succeeded", requestedAt: "2026-06-06T09:00:00Z", finishedAt: "2026-06-06T09:05:00Z", resultSummary: "ok" }, null],
  ])("TC-12: hides the auto-refreshing hint when neither kind is in-flight (%s)", async (_label, crawlInfo, enrichInfo) => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlInfo,
      enrichment: enrichInfo,
    });

    render(<AdminPage account={ACCOUNT} />);
    const freshnessLine = await screen.findByTestId("freshness-line");
    expect(freshnessLine.textContent).not.toMatch(/auto-refreshing/i);
  });

  it("TC-13: freshness age keeps advancing with no auto-poll active, and no extra fetch is made", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(getAdminTriggerStatus).toHaveBeenCalledTimes(1);

      await act(async () => {
        vi.advanceTimersByTime(30000);
      });

      expect(screen.getByTestId("freshness-line")).toHaveTextContent(/updated 30s ago/i);
      expect(screen.getByTestId("freshness-line").textContent).not.toMatch(/auto-refreshing/i);
      expect(getAdminTriggerStatus).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-15, TC-17, TC-19, TC-21, TC-23: relative timestamp rendering with absolute title", () => {
  it("TC-15: requestedAt from 3 minutes ago reads '3 min ago' with a title carrying the absolute value", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "running", requestedAt: isoSecondsBefore(NOW_ISO, 3 * 60), startedAt: isoSecondsBefore(NOW_ISO, 3 * 60), finishedAt: null },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const requestedEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-requested-at");
      expect(requestedEl).toHaveTextContent("3 min ago");
      expect(requestedEl).toHaveAttribute("title");
      expect(requestedEl.getAttribute("title")).toMatch(/2026/);
      expect(requestedEl.getAttribute("title")).not.toBe(isoSecondsBefore(NOW_ISO, 3 * 60));
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-17: finishedAt from 2 hours ago reads '2 h ago' with an absolute title", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "succeeded",
          requestedAt: isoSecondsBefore(NOW_ISO, 2 * 60 * 60 + 60),
          finishedAt: isoSecondsBefore(NOW_ISO, 2 * 60 * 60),
          resultSummary: "ok",
        },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const finishedEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-finished-at");
      expect(finishedEl).toHaveTextContent("2 h ago");
      expect(finishedEl).toHaveAttribute("title");
      expect(finishedEl.getAttribute("title")).toMatch(/2026/);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-19: finishedAt from 45 seconds ago reads '45s ago'", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "succeeded",
          requestedAt: isoSecondsBefore(NOW_ISO, 90),
          finishedAt: isoSecondsBefore(NOW_ISO, 45),
          resultSummary: "ok",
        },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const finishedEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-finished-at");
      expect(finishedEl).toHaveTextContent("45s ago");
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-21: requestedAt effectively 'now' reads '0s ago'", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "running", requestedAt: NOW_ISO, startedAt: NOW_ISO, finishedAt: null },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const requestedEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-requested-at");
      expect(requestedEl).toHaveTextContent("0s ago");
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-23: finishedAt from 89 seconds ago reads '1 min ago'", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_ISO));
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "succeeded",
          requestedAt: isoSecondsBefore(NOW_ISO, 120),
          finishedAt: isoSecondsBefore(NOW_ISO, 89),
          resultSummary: "ok",
        },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const finishedEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-finished-at");
      expect(finishedEl).toHaveTextContent("1 min ago");
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-25, TC-27, TC-29, TC-31, TC-33..TC-36, TC-38, TC-40: duration for terminal runs", () => {
  it("TC-25: succeeded, 42s elapsed, shows run-duration '42s'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "succeeded", requestedAt: "2026-06-06T10:00:00Z", finishedAt: "2026-06-06T10:00:42Z", resultSummary: "ok" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const durationEl = within(await screen.findByTestId("kind-panel-crawl")).getByTestId("run-duration");
    expect(durationEl).toHaveTextContent("42s");
  });

  it("TC-27: failed, 2m14s elapsed, shows run-duration '2m 14s'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "failed", requestedAt: "2026-06-06T10:00:00Z", finishedAt: "2026-06-06T10:02:14Z", errorReason: "boom" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const durationEl = within(await screen.findByTestId("kind-panel-crawl")).getByTestId("run-duration");
    expect(durationEl).toHaveTextContent("2m 14s");
  });

  it("TC-29: cancelled, 1h5m30s elapsed, shows run-duration '1h 5m'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "cancelled", requestedAt: "2026-06-06T09:00:00Z", finishedAt: "2026-06-06T10:05:30Z", resultSummary: "cancelled" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const durationEl = within(await screen.findByTestId("kind-panel-crawl")).getByTestId("run-duration");
    expect(durationEl).toHaveTextContent("1h 5m");
  });

  it("TC-31: exactly 60s elapsed shows run-duration '1m 0s'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "succeeded", requestedAt: "2026-06-06T10:00:00Z", finishedAt: "2026-06-06T10:01:00Z", resultSummary: "ok" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const durationEl = within(await screen.findByTestId("kind-panel-crawl")).getByTestId("run-duration");
    expect(durationEl).toHaveTextContent("1m 0s");
  });

  it("TC-33: exactly 3600s elapsed shows run-duration '1h 0m'", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "succeeded", requestedAt: "2026-06-06T09:00:00Z", finishedAt: "2026-06-06T10:00:00Z", resultSummary: "ok" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const durationEl = within(await screen.findByTestId("kind-panel-crawl")).getByTestId("run-duration");
    expect(durationEl).toHaveTextContent("1h 0m");
  });

  it.each(["queued", "running", "cancel_requested"])(
    "TC-34: no run-duration for a non-terminal status '%s', even with both timestamps present",
    async (statusValue) => {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: statusValue, requestedAt: "2026-06-06T10:00:00Z", startedAt: "2026-06-06T10:00:01Z", finishedAt: "2026-06-06T10:00:42Z" },
      });

      render(<AdminPage account={ACCOUNT} />);
      await screen.findByTestId("kind-panel-crawl");
      expect(screen.queryByTestId("run-duration")).not.toBeInTheDocument();
    }
  );

  it("TC-35: no run-duration when runInfo is null (never run)", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);
    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).getByTestId("run-info-never")).toBeInTheDocument();
    expect(within(crawlPanel).queryByTestId("run-duration")).not.toBeInTheDocument();
  });

  it.each([
    ["missing requestedAt", { status: "succeeded", requestedAt: null, finishedAt: "2026-06-06T10:00:42Z", resultSummary: "ok" }],
    ["missing finishedAt", { status: "succeeded", requestedAt: "2026-06-06T10:00:00Z", finishedAt: null, resultSummary: "ok" }],
  ])("TC-36: no run-duration and no crash/NaN when a terminal run is missing a timestamp (%s)", async (_label, crawlInfo) => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlInfo,
    });

    const { container } = render(<AdminPage account={ACCOUNT} />);
    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).queryByTestId("run-duration")).not.toBeInTheDocument();
    expect(container.textContent).not.toMatch(/NaN/);
    expect(container.textContent).not.toMatch(/Invalid Date/);
  });

  it("TC-38: no 'NaN'/'Invalid Date' text and no crash for an unparseable date string", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "succeeded", requestedAt: "not-a-date", finishedAt: "2026-06-06T10:00:42Z", resultSummary: "ok" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(crawlPanel.textContent).not.toMatch(/NaN/);
    expect(crawlPanel.textContent).not.toMatch(/Invalid Date/);
  });

  it("TC-40: no negative duration text and no crash on clock-skew data (finishedAt before requestedAt)", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "succeeded", requestedAt: "2026-06-06T10:05:00Z", finishedAt: "2026-06-06T10:00:00Z", resultSummary: "ok" },
    });

    render(<AdminPage account={ACCOUNT} />);
    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(crawlPanel.textContent).not.toMatch(/-\d/);
    expect(screen.queryByTestId("run-duration")?.textContent ?? "").not.toMatch(/^-/);
  });
});

describe("TC-41..TC-43: Refresh (manual and auto) never triggers the verification-code flow", () => {
  it("TC-41: manual Refresh click never calls requestVerification and shows no new code-entry UI", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);

    render(<AdminPage account={ACCOUNT} />);
    const refreshBtn = await screen.findByTestId("refresh-btn");

    await act(async () => {
      fireEvent.click(refreshBtn);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(getAdminTriggerStatus).toHaveBeenCalled();
    expect(requestVerification).not.toHaveBeenCalled();
    expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();
    expect(screen.queryByTestId("code-input-enrichment")).not.toBeInTheDocument();
  });

  it("TC-42: auto-poll tick never calls requestVerification and shows no code-entry UI", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: { status: "running", requestedAt: "2026-06-06T10:00:00Z", startedAt: "2026-06-06T10:00:05Z", finishedAt: null },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const callsBefore = getAdminTriggerStatus.mock.calls.length;

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(getAdminTriggerStatus.mock.calls.length).toBeGreaterThan(callsBefore);
      expect(requestVerification).not.toHaveBeenCalled();
      expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();
      expect(screen.queryByTestId("code-input-enrichment")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("TC-43: with codeRequired=true, manual Refresh and an auto-poll tick both succeed without any code prompt", async () => {
    vi.useFakeTimers();
    try {
      // Story #398 reopen (#565): a QUEUED run (not RUNNING) is the state that
      // still blocks a new trigger (the DB partial unique index allows one
      // running plus one queued per kind), so it is the one that hides the
      // code-entry gate; RUNNING no longer does (C38/C41).
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_CODE_REQUIRED,
        crawl: { status: "queued", requestedAt: "2026-06-06T10:00:00Z" },
      });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const refreshBtn = screen.getByTestId("refresh-btn");
      await act(async () => {
        fireEvent.click(refreshBtn);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(requestVerification).not.toHaveBeenCalled();
      expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();

      const callsBefore = getAdminTriggerStatus.mock.calls.length;
      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(getAdminTriggerStatus.mock.calls.length).toBeGreaterThan(callsBefore);
      expect(requestVerification).not.toHaveBeenCalled();
      expect(screen.queryByTestId("code-input-crawl")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #513 (sub-issue #520): render live crawl progress on the run panel.
 * Cases: TC-513-U1..U14 (docs/test-cases/513-crawler-visibility.md §3).
 * Testability hook: the progress detail's container carries
 * data-testid="run-progress", per the QAE spec.
 * ───────────────────────────────────────────────────────────────────────── */

function crawlWithProgress(status, overrides = {}, progressOverrides = {}) {
  return {
    status,
    requestedAt: "2026-06-06T10:00:00Z",
    startedAt: "2026-06-06T10:00:05Z",
    finishedAt: null,
    resultSummary: null,
    errorReason: null,
    progress: {
      targetsVisited: 2,
      newPosts: 30,
      currentCompany: "Klaviyo",
      currentSourceType: "greenhouse",
      lastCompany: null,
      lastSourceType: null,
      lastFoundPosts: null,
      lastNewPosts: null,
      updatedAt: "2026-06-06T10:03:00Z",
      ...progressOverrides,
    },
    ...overrides,
  };
}

describe("TC-513-U1: Queued crawl renders with no progress detail", () => {
  it("hides run-progress and still shows the queued status", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "queued", requestedAt: "2026-06-06T10:00:00Z", progress: null },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
    expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/queued/i);
  });
});

describe("TC-513-U2: Running crawl, progress not yet reported", () => {
  it("shows the running indicator with no progress detail beneath it", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "running",
        requestedAt: "2026-06-06T10:00:00Z",
        startedAt: "2026-06-06T10:00:05Z",
        finishedAt: null,
        progress: null,
      },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).getByTestId("running-indicator")).toBeInTheDocument();
    expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
  });
});

describe("TC-513-U3: Running, first target in flight, zero completed", () => {
  it("shows the in-flight target and two distinct zero counters", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "running",
        {},
        { targetsVisited: 0, newPosts: 0, currentCompany: "Klaviyo", currentSourceType: "greenhouse" }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const progressEl = within(crawlPanel).getByTestId("run-progress");
    expect(progressEl).toHaveTextContent(/Klaviyo/);
    expect(progressEl).toHaveTextContent(/greenhouse/);
    const zeroMatches = progressEl.textContent.match(/\b0\b/g) || [];
    expect(zeroMatches.length).toBeGreaterThanOrEqual(2);
  });
});

describe("TC-513-U4: Running, N completed shows accumulated counters, current, and last", () => {
  it("shows targetsVisited, newPosts, current company/source, and last company/found/new", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "running",
        {},
        {
          targetsVisited: 3,
          newPosts: 47,
          currentCompany: "Figma",
          currentSourceType: "lever",
          lastCompany: "Klaviyo",
          lastSourceType: "greenhouse",
          lastFoundPosts: 142,
          lastNewPosts: 16,
        }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const progressEl = within(crawlPanel).getByTestId("run-progress");
    expect(progressEl).toHaveTextContent(/3/);
    expect(progressEl).toHaveTextContent(/47/);
    expect(progressEl).toHaveTextContent(/Figma/);
    expect(progressEl).toHaveTextContent(/Klaviyo/);
    expect(progressEl).toHaveTextContent(/142/);
    expect(progressEl).toHaveTextContent(/16/);
  });
});

describe("TC-513-U5: lastFoundPosts=0/lastNewPosts=0 renders as a normal value", () => {
  it("shows a zero for the last target's found/new pair with no alert element", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "running",
        {},
        {
          targetsVisited: 4,
          newPosts: 30,
          currentCompany: "NextCo",
          currentSourceType: "lever",
          lastCompany: "Acme",
          lastSourceType: "greenhouse",
          lastFoundPosts: 0,
          lastNewPosts: 0,
        }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    const progressEl = within(crawlPanel).getByTestId("run-progress");
    expect(progressEl).toHaveTextContent(/Acme/);
    const zeroMatches = progressEl.textContent.match(/\b0\b/g) || [];
    expect(zeroMatches.length).toBeGreaterThanOrEqual(1);
    expect(within(crawlPanel).queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("TC-513-U6: progress advances between two consecutive poll ticks", () => {
  it("shows strictly greater counters after the 5s auto-poll", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus
        .mockResolvedValueOnce({
          ...STATUS_NO_CODE_ENABLED,
          crawl: crawlWithProgress("running", {}, { targetsVisited: 2, newPosts: 30 }),
        })
        .mockResolvedValue({
          ...STATUS_NO_CODE_ENABLED,
          crawl: crawlWithProgress("running", {}, { targetsVisited: 3, newPosts: 47 }),
        });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlPanel = screen.getByTestId("kind-panel-crawl");
      let progressEl = within(crawlPanel).getByTestId("run-progress");
      expect(progressEl).toHaveTextContent(/2/);
      expect(progressEl).toHaveTextContent(/30/);

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      progressEl = within(crawlPanel).getByTestId("run-progress");
      expect(progressEl).toHaveTextContent(/3/);
      expect(progressEl).toHaveTextContent(/47/);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-513-U7: in-flight target rotates to 'last' across a poll boundary", () => {
  it("shows the new current target and the previous target's own numbers as last", async () => {
    vi.useFakeTimers();
    try {
      getAdminTriggerStatus
        .mockResolvedValueOnce({
          ...STATUS_NO_CODE_ENABLED,
          crawl: crawlWithProgress(
            "running",
            {},
            {
              currentCompany: "Klaviyo",
              currentSourceType: "greenhouse",
              lastCompany: null,
              lastSourceType: null,
              lastFoundPosts: null,
              lastNewPosts: null,
            }
          ),
        })
        .mockResolvedValue({
          ...STATUS_NO_CODE_ENABLED,
          crawl: crawlWithProgress(
            "running",
            {},
            {
              currentCompany: "Figma",
              currentSourceType: "lever",
              lastCompany: "Klaviyo",
              lastSourceType: "greenhouse",
              lastFoundPosts: 142,
              lastNewPosts: 16,
            }
          ),
        });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlPanel = screen.getByTestId("kind-panel-crawl");
      const progressEl = within(crawlPanel).getByTestId("run-progress");
      expect(progressEl).toHaveTextContent(/Figma/);
      expect(progressEl).toHaveTextContent(/Klaviyo/);
      expect(progressEl).toHaveTextContent(/142/);
      expect(progressEl).toHaveTextContent(/16/);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-513-U8: identical progress across two polls renders without a stuck/error indicator", () => {
  it("keeps the same content and shows no alert element in the crawl panel", async () => {
    vi.useFakeTimers();
    try {
      const crawl = crawlWithProgress(
        "running",
        {},
        { targetsVisited: 5, newPosts: 40, currentCompany: "SlowCo", currentSourceType: "workday" }
      );
      getAdminTriggerStatus.mockResolvedValue({ ...STATUS_NO_CODE_ENABLED, crawl });

      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlPanel = screen.getByTestId("kind-panel-crawl");
      const before = within(crawlPanel).getByTestId("run-progress").textContent;

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
        await Promise.resolve();
      });

      const after = within(crawlPanel).getByTestId("run-progress").textContent;
      expect(after).toBe(before);
      expect(within(crawlPanel).queryByRole("alert")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-513-U9: cancel_requested keeps showing the same live-progress detail as running", () => {
  it("shows run-progress alongside the existing 'Cancelling...' label", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "cancel_requested",
        {},
        {
          targetsVisited: 3,
          newPosts: 47,
          currentCompany: "Figma",
          currentSourceType: "lever",
          lastCompany: "Klaviyo",
          lastSourceType: "greenhouse",
          lastFoundPosts: 142,
          lastNewPosts: 16,
        }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent("Cancelling…");
    expect(within(crawlPanel).getByTestId("run-progress")).toHaveTextContent(/Figma/);
  });
});

describe("TC-513-U10: terminal succeeded hands off entirely to resultSummary", () => {
  it("hides run-progress even though the payload's progress is non-null; newPosts=0 is not an error", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "succeeded",
        { finishedAt: "2026-06-06T10:10:00Z", resultSummary: "Batch complete: 5 targets visited, 0 new posts" },
        { targetsVisited: 5, newPosts: 0, currentCompany: null, currentSourceType: null }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
    expect(within(crawlPanel).getByTestId("run-result-summary")).toHaveTextContent(
      "Batch complete: 5 targets visited, 0 new posts"
    );
    expect(within(crawlPanel).queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("TC-513-U11: terminal cancelled hands off to resultSummary", () => {
  it("hides run-progress and shows no 'currently crawling' text", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "cancelled",
        {
          finishedAt: "2026-06-06T10:10:00Z",
          resultSummary: "Batch cancelled: 4 targets visited, 30 new posts before stop",
        },
        { targetsVisited: 4, newPosts: 30, currentCompany: null, currentSourceType: null }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
    expect(within(crawlPanel).getByTestId("run-result-summary")).toHaveTextContent(
      "Batch cancelled: 4 targets visited, 30 new posts before stop"
    );
    expect(crawlPanel.textContent).not.toMatch(/crawling/i);
  });
});

describe("TC-513-U12: terminal failed hands off, errorReason unaffected by leftover progress values", () => {
  it("hides run-progress and still shows the error reason", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: crawlWithProgress(
        "failed",
        { finishedAt: "2026-06-06T10:10:00Z", resultSummary: null, errorReason: "upstream 503" },
        { targetsVisited: 4, currentCompany: null, currentSourceType: null }
      ),
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
    expect(within(crawlPanel).getByTestId("run-error-reason")).toHaveTextContent("upstream 503");
  });
});

describe("TC-513-U13: enrichment panel never shows progress, at any status", () => {
  it.each([
    [
      "running",
      {
        status: "running",
        requestedAt: "2026-06-06T10:00:00Z",
        startedAt: "2026-06-06T10:00:05Z",
        finishedAt: null,
        progress: null,
      },
    ],
    [
      "succeeded",
      {
        status: "succeeded",
        requestedAt: "2026-06-06T10:00:00Z",
        finishedAt: "2026-06-06T10:05:00Z",
        resultSummary: "enriched 12 postings",
        progress: null,
      },
    ],
  ])("hides run-progress for enrichment status '%s'", async (_label, enrichmentInfo) => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      enrichment: enrichmentInfo,
    });

    render(<AdminPage account={ACCOUNT} />);

    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");
    expect(within(enrichPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
  });
});

describe("TC-513-U14: a pre-feature payload with no progress key renders identically to today", () => {
  it("renders no run-progress and no throw when the crawl object omits the progress key entirely", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      getAdminTriggerStatus.mockResolvedValue({
        ...STATUS_NO_CODE_ENABLED,
        crawl: {
          status: "succeeded",
          requestedAt: "2026-06-06T09:55:00Z",
          finishedAt: "2026-06-06T10:00:00Z",
          resultSummary: "crawled 5 targets, 20 new postings",
          errorReason: null,
        },
      });

      render(<AdminPage account={ACCOUNT} />);

      const crawlPanel = await screen.findByTestId("kind-panel-crawl");
      expect(within(crawlPanel).queryByTestId("run-progress")).not.toBeInTheDocument();
      expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/succeeded/i);
      expect(within(crawlPanel).getByTestId("run-result-summary")).toHaveTextContent(
        "crawled 5 targets, 20 new postings"
      );
      expect(within(crawlPanel).getByTestId("run-finished-at")).toBeInTheDocument();

      const reactWarnings = errorSpy.mock.calls.filter(
        ([msg]) => typeof msg === "string" && /warning/i.test(msg)
      );
      expect(reactWarnings).toHaveLength(0);
    } finally {
      errorSpy.mockRestore();
    }
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #398 (ticket #565): requesting state, last-run time/outcome/origin
 * per kind, no-targets copy, empty run-history state.
 * Cases: C31..C37
 * ───────────────────────────────────────────────────────────────────────── */

describe("C31 (AC11): clicking Trigger Crawl shows a local 'Requesting' state immediately", () => {
  it("shows the requesting state synchronously after the click, before the POST resolves", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    let resolveTrigger;
    triggerAdminPass.mockImplementation(
      () => new Promise((resolve) => { resolveTrigger = resolve; })
    );

    render(<AdminPage account={ACCOUNT} />);
    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");

    await act(async () => {
      fireEvent.click(crawlBtn);
      await Promise.resolve();
    });

    expect(screen.getByTestId("trigger-requesting-crawl")).toHaveTextContent(/requesting/i);

    await act(async () => {
      resolveTrigger({ id: "1", kind: "crawl", status: "queued", requestedAt: "2026-06-06T10:00:00Z" });
      await Promise.resolve();
      await Promise.resolve();
    });
  });
});

describe("C32 (AC11): after the 202 and status refetch, the UI moves from Requesting to queued", () => {
  it("clears the requesting state and shows queued once the trigger POST and follow-up poll resolve", async () => {
    const queuedStatus = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "queued", requestedAt: "2026-06-06T10:00:00Z" },
    };
    getAdminTriggerStatus.mockResolvedValueOnce(STATUS_NO_CODE_ENABLED).mockResolvedValue(queuedStatus);
    triggerAdminPass.mockResolvedValue({
      id: "1",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:00:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);
    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      expect(screen.queryByTestId("trigger-requesting-crawl")).not.toBeInTheDocument();
    });

    await waitFor(() => {
      expect(within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-status")).toHaveTextContent(/queued/i);
    });
  });
});

describe("C33 (AC12): a 409 rejection clears Requesting and shows the conflict message", () => {
  it("clears the requesting state and shows a conflict error toast on 409", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockRejectedValue(
      new ApiError(409, "Conflict", { error: "Conflict", message: "already running" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);
    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-crawl")).toHaveTextContent(/already in progress/i);
    });
    expect(screen.queryByTestId("trigger-requesting-crawl")).not.toBeInTheDocument();
  });
});

describe("C34 (AC12): a 403 disabled rejection clears Requesting and disables the trigger button", () => {
  it("clears the requesting state and disables the trigger button on 403", async () => {
    getAdminTriggerStatus.mockResolvedValue(STATUS_NO_CODE_ENABLED);
    triggerAdminPass.mockRejectedValue(
      new ApiError(403, "Forbidden", { error: "Forbidden", message: "Triggering Disabled" })
    );

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);
    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      expect(screen.getByTestId("trigger-error-crawl")).toHaveTextContent(/disabled/i);
    });
    expect(screen.queryByTestId("trigger-requesting-crawl")).not.toBeInTheDocument();
    expect(screen.getByTestId("trigger-btn-crawl")).toBeDisabled();
  });
});

describe("C35 (AC9): the panel shows each kind's last finished run, its origin, and its outcome", () => {
  it("shows scheduled origin for crawl and manual origin for enrichment, with finished time and outcome text", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      lastCrawlRun: {
        id: "c1",
        finishedAt: "2026-06-06T09:48:00Z",
        status: "succeeded",
        outcome: "completed",
        origin: "scheduled",
        resultSummary: "crawled 10 targets, 37 new postings",
      },
      lastEnrichmentRun: {
        id: "e1",
        finishedAt: "2026-06-06T09:50:00Z",
        status: "failed",
        outcome: "failed",
        origin: "manual",
        errorReason: "model timeout",
      },
    });

    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-06T10:00:00Z"));
    try {
      render(<AdminPage account={ACCOUNT} />);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      const crawlPanel = screen.getByTestId("kind-panel-crawl");
      expect(within(crawlPanel).getByTestId("last-run-crawl-when")).toHaveTextContent(/12 min ago/i);
      expect(within(crawlPanel).getByTestId("last-run-crawl-origin")).toHaveTextContent(/automatic/i);
      expect(within(crawlPanel).getByTestId("last-run-crawl-outcome")).toHaveTextContent(
        "crawled 10 targets, 37 new postings"
      );

      const enrichPanel = screen.getByTestId("kind-panel-enrichment");
      expect(within(enrichPanel).getByTestId("last-run-enrichment-when")).toHaveTextContent(/10 min ago/i);
      expect(within(enrichPanel).getByTestId("last-run-enrichment-origin")).toHaveTextContent(/manual/i);
      expect(within(enrichPanel).getByTestId("last-run-enrichment-outcome")).toHaveTextContent(/model timeout/i);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("C36 (AC6): a no_targets outcome reads as 'no more targets to crawl', never 'crawled 0'", () => {
  it("shows the fixed no-targets copy regardless of resultSummary content", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      lastCrawlRun: {
        id: "c2",
        finishedAt: "2026-06-06T09:55:00Z",
        status: "succeeded",
        outcome: "no_targets",
        origin: "scheduled",
        resultSummary: "crawled 0 targets",
      },
    });

    render(<AdminPage account={ACCOUNT} />);
    const crawlPanel = await screen.findByTestId("kind-panel-crawl");

    const outcomeEl = within(crawlPanel).getByTestId("last-run-crawl-outcome");
    expect(outcomeEl).toHaveTextContent(/no more targets to crawl/i);
    expect(outcomeEl.textContent).not.toMatch(/crawled 0/i);
  });
});

describe("C37 (AC10): a kind with no finished run history shows an empty state, not an error", () => {
  it("shows a no-run-history message for enrichment without crashing", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      lastCrawlRun: null,
      lastEnrichmentRun: null,
    });

    render(<AdminPage account={ACCOUNT} />);
    const enrichPanel = await screen.findByTestId("kind-panel-enrichment");

    expect(within(enrichPanel).getByTestId("last-run-enrichment-empty")).toHaveTextContent(/no run history/i);
    expect(within(enrichPanel).queryByTestId("last-run-enrichment")).not.toBeInTheDocument();
  });
});

/* ─────────────────────────────────────────────────────────────────────────
 * Story #398 reopen (ticket #565, pre-PR gate): a manual trigger fired while
 * a run of that kind is RUNNING is now accepted and lands as `queued`
 * instead of a 409; a second `queued` request for the same kind still
 * 409s. The in-progress run must also show its origin.
 * Cases: C38..C41
 * ───────────────────────────────────────────────────────────────────────── */

describe("C38: the trigger button stays enabled while a run of that kind is RUNNING", () => {
  it("allows a manual trigger while crawl is running, and calls triggerAdminPass", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "running",
        requestedAt: "2026-06-06T10:00:00Z",
        startedAt: "2026-06-06T10:00:05Z",
        finishedAt: null,
        origin: "scheduled",
      },
    });
    triggerAdminPass.mockResolvedValue({
      id: "1",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:05:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    expect(crawlBtn).not.toBeDisabled();

    await user.click(crawlBtn);

    await waitFor(() => expect(triggerAdminPass).toHaveBeenCalledWith({ kind: "crawl" }));
  });
});

describe("C39: after a manual trigger while running, the accepted request is shown as queued", () => {
  it("shows a queued state, distinct from running, once the POST and follow-up poll resolve", async () => {
    const runningStatus = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "running",
        requestedAt: "2026-06-06T09:00:00Z",
        startedAt: "2026-06-06T09:00:05Z",
        finishedAt: null,
        origin: "scheduled",
      },
    };
    const queuedAfterManual = {
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "queued",
        requestedAt: "2026-06-06T10:05:00Z",
        origin: "manual",
      },
    };
    getAdminTriggerStatus.mockResolvedValueOnce(runningStatus).mockResolvedValue(queuedAfterManual);
    triggerAdminPass.mockResolvedValue({
      id: "2",
      kind: "crawl",
      status: "queued",
      requestedAt: "2026-06-06T10:05:00Z",
    });

    const user = userEvent.setup();
    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    await user.click(crawlBtn);

    await waitFor(() => {
      const statusEl = within(screen.getByTestId("kind-panel-crawl")).getByTestId("run-status");
      expect(statusEl).toHaveTextContent(/queued/i);
      expect(statusEl).not.toHaveTextContent(/running/i);
    });
  });
});

describe("C40: the trigger button is disabled while a run of that kind is already QUEUED", () => {
  it("keeps trigger-btn-crawl disabled, since the backend still 409s a second queued request", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: { status: "queued", requestedAt: "2026-06-06T10:00:00Z", origin: "manual" },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlBtn = await screen.findByTestId("trigger-btn-crawl");
    expect(crawlBtn).toBeDisabled();

    fireEvent.click(crawlBtn);
    expect(triggerAdminPass).not.toHaveBeenCalled();
  });
});

describe("C41: origin is visible on an in-progress automatic run, not just the last-run block", () => {
  it("shows 'Automatic' next to the running status for a scheduled crawl", async () => {
    getAdminTriggerStatus.mockResolvedValue({
      ...STATUS_NO_CODE_ENABLED,
      crawl: {
        status: "running",
        requestedAt: "2026-06-06T10:00:00Z",
        startedAt: "2026-06-06T10:00:05Z",
        finishedAt: null,
        origin: "scheduled",
      },
    });

    render(<AdminPage account={ACCOUNT} />);

    const crawlPanel = await screen.findByTestId("kind-panel-crawl");
    expect(within(crawlPanel).getByTestId("run-status")).toHaveTextContent(/running/i);
    expect(within(crawlPanel).getByTestId("run-origin")).toHaveTextContent(/automatic/i);
  });
});
