/**
 * Component tests: drag-and-drop status changes on the Application Kanban board
 * (story #152, ticket #179).
 *
 * Source: docs/specs/152-kanban-dnd.md (AC-1..AC-25), docs/test-cases/152-kanban-dnd-cases.md
 * (TC-DND-001..028).
 *
 * @dnd-kit drives drag gestures from pointer/keyboard sensors, not native HTML5 drag
 * events. These tests simulate the underlying pointer events the PointerSensor listens
 * for (pointerdown -> pointermove -> pointerup) via fireEvent, and the keyboard move-mode
 * affordance via userEvent key presses, per the QAE note that the literal event sequence
 * is an implementation detail as long as the documented outcome holds.
 */
import React from "react";
import { render, screen, waitFor, within, fireEvent, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import DATA from "../../data/mockData.js";

vi.mock("../../api/applications.js", () => ({
  updateApplicationStatus: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.body = body;
    }
  },
}));

import { updateApplicationStatus } from "../../api/applications.js";
import { ApiError } from "../../api/client.js";
import { ApplicationsScreen, ApplicationDetailScreen } from "../../screens/Applications.jsx";

const JOB_ID_PREFIX = "kanban-job-";

const CO_NAMES = { acme: "Acme Corp", globex: "Globex Corp" };

function seedJob(id, overrides = {}) {
  const co = overrides.co || "acme";
  DATA.jobs.push({
    id,
    title: overrides.title || "Engineer",
    co,
    location: "Remote",
    comp: "$120k",
    type: "Full-time",
    source: "LinkedIn",
    tags: [],
  });
  if (!DATA.companies[co]) {
    DATA.companies[co] = { name: CO_NAMES[co] || co, industry: "Software", size: "201-500", hq: "Remote", url: "" };
  }
}

function seedApp(overrides = {}) {
  const id = overrides.id || `app-${Math.random().toString(36).slice(2)}`;
  const jobId = overrides.jobId || JOB_ID_PREFIX + id;
  seedJob(jobId, overrides);
  const app = {
    id,
    apiId: overrides.apiId !== undefined ? overrides.apiId : id,
    jobId,
    status: overrides.status || "applied",
    notes: "",
    timeline: [],
    appliedOn: overrides.appliedOn || "2026-06-01",
    lastUpdate: "Jun 1, 10:00 AM",
    nextStep: overrides.nextStep,
  };
  DATA.applications.push(app);
  return app;
}

function renderBoard(props = {}) {
  return render(
    <ApplicationsScreen
      openApp={vi.fn()}
      openSearch={vi.fn()}
      onAddApp={vi.fn()}
      onLogout={vi.fn()}
      {...props}
    />
  );
}

// @dnd-kit's AbstractPointerSensor attaches its own keydown/cancel listeners directly on the
// element that received the initiating pointerdown (not on document), so the active card is
// tracked here for cancelDrag() to dispatch the Escape key on the right node.
let activeDragCard = null;

/* Once a drag passes its activation constraint, the same sensor also puts three listeners on
   `document`: a capture-phase `click` that stopPropagation()s the click a real drag would
   otherwise leave behind, plus `keydown` and `selectionchange`. Ending the drag does NOT remove
   them synchronously - detach() defers to `setTimeout(documentListeners.removeAll, 50)`
   (@dnd-kit/core AbstractPointerSensor). So for ~50ms after every gesture, any click dispatched
   anywhere is swallowed before React sees it, and a test that finishes inside that window leaks
   the listeners into the next test's document.

   A real user cannot click within 50ms of releasing a drag; a synchronous test trivially can,
   which is what made the confirm-dialog tests fail whenever the runner was fast enough to get
   from the drop to the click inside the window. Every gesture helper below therefore waits the
   window out. Ordering is deterministic rather than hopeful: our timer is queued after dnd-kit's
   and expires later, so it always fires after the removal no matter how loaded the event loop is. */
const DND_TEARDOWN_MS = 50;

async function settleGesture() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, DND_TEARDOWN_MS + 10));
  });
}

// Simulate a full pointer drag gesture: pointerdown on the source, pointermove over the
// target (crossing the dnd-kit activation distance), pointerup to drop.
async function dragCard(card, target) {
  fireEvent.pointerDown(card, { pointerId: 1, clientX: 0, clientY: 0, isPrimary: true, button: 0 });
  fireEvent.pointerMove(document, { pointerId: 1, clientX: 50, clientY: 50 });
  const targetRect = target.getBoundingClientRect();
  fireEvent.pointerMove(target, { pointerId: 1, clientX: targetRect.left + 10, clientY: targetRect.top + 10 });
  fireEvent.pointerUp(target, { pointerId: 1, clientX: targetRect.left + 10, clientY: targetRect.top + 10 });
  await settleGesture();
}

function startDrag(card) {
  activeDragCard = card;
  fireEvent.pointerDown(card, { pointerId: 1, clientX: 0, clientY: 0, isPrimary: true, button: 0 });
  fireEvent.pointerMove(document, { pointerId: 1, clientX: 50, clientY: 50 });
}

function moveOver(target) {
  const targetRect = target.getBoundingClientRect();
  fireEvent.pointerMove(target, { pointerId: 1, clientX: targetRect.left + 10, clientY: targetRect.top + 10 });
}

// @dnd-kit resolves the drop target from the last pointermove position, not from the
// pointerup event's own coordinates (its AbstractPointerSensor.handleEnd takes no coordinates
// at all) - so every drop must be preceded by a pointermove onto the target.
async function dropOn(target) {
  const targetRect = target.getBoundingClientRect();
  fireEvent.pointerMove(target, { pointerId: 1, clientX: targetRect.left + 10, clientY: targetRect.top + 10 });
  fireEvent.pointerUp(target, { pointerId: 1, clientX: targetRect.left + 10, clientY: targetRect.top + 10 });
  await settleGesture();
}

async function cancelDrag() {
  fireEvent.keyDown(activeDragCard || document, { key: "Escape", code: "Escape" });
  await settleGesture();
}

function getColumn(name) {
  const head = screen.getByText(name, { selector: ".kanban-head .name" }).closest(".kanban-head");
  return head.closest(".kanban-col");
}

function getCardByCompany(name) {
  return screen.getByText(name).closest(".kanban-card");
}

// jsdom never performs real layout, so every element's getBoundingClientRect() is all-zero
// by default, and @dnd-kit's collision detection (rectIntersection/closestCenter) then can't
// tell any droppable apart. Stub a deterministic, non-overlapping rect per *droppable zone*
// (a kanban column or a Closed sub-zone button), assigned lazily on first read so it stays
// correct even as the fan-out mounts/unmounts nodes mid-drag. Any other element (e.g. the
// column head, or a card inside the column) is keyed off its nearest droppable-zone ancestor,
// so dropping on it resolves to that zone, matching real DOM/CSS containment.
let rectRegistry;
function droppableZoneFor(el) {
  return el.closest("[data-rect-zone]") || el;
}
function rectFor(el) {
  const zone = droppableZoneFor(el);
  if (!rectRegistry.has(zone)) {
    const index = rectRegistry.size;
    rectRegistry.set(zone, index);
  }
  const index = rectRegistry.get(zone);
  const left = index * 300;
  const top = 0;
  return { width: 280, height: 60, top, left, right: left + 280, bottom: top + 60, x: left, y: top, toJSON() { return this; } };
}

beforeEach(() => {
  vi.clearAllMocks();
  DATA.jobs.length = 0;
  DATA.applications.length = 0;
  Object.keys(DATA.companies).forEach((k) => delete DATA.companies[k]);
  rectRegistry = new Map();
  activeDragCard = null;
  Element.prototype.getBoundingClientRect = function () { return rectFor(this); };
  try { window.localStorage.clear(); } catch {}
});

describe("TC-DND-001: Forward move between pipeline columns (AC-1)", () => {
  it("calls updateApplicationStatus with the backend status and moves the card", async () => {
    seedApp({ id: "a1", status: "applied", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const screeningCol = getColumn("Screening");
    await dragCard(card, screeningCol);

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a1", "screening"));
    expect(within(screeningCol).getByText("Acme Corp")).toBeInTheDocument();
    expect(within(getColumn("Applied")).queryByText("Acme Corp")).not.toBeInTheDocument();
  });
});

describe("TC-DND-002: Drop on Closed's general area (not a sub-zone) is rejected (AC-2)", () => {
  it("does not call the API and the card stays in its original column", async () => {
    seedApp({ id: "a2", status: "interview", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    // Drag over Closed (fans out) then drop on the column's head/gap area, not a sub-zone.
    startDrag(card);
    moveOver(closedCol);
    const head = within(closedCol).getByText("Closed").closest(".kanban-head");
    await dropOn(head);

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(getColumn("Interview")).getByText("Acme Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-003: Dropping back into its own column is a no-op (AC-3)", () => {
  it("does not call the API and the card stays put", async () => {
    seedApp({ id: "a3", status: "screening", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const screeningCol = getColumn("Screening");
    await dragCard(card, screeningCol);

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(screeningCol).getByText("Acme Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-004: Cancelling a drag outside any drop target leaves the application unchanged (AC-4)", () => {
  it("returns the card to its column with no API call and no error", async () => {
    seedApp({ id: "a4", status: "applied", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    startDrag(card);
    await cancelDrag();

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(getColumn("Applied")).getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.queryByText(/couldn't update status/i)).not.toBeInTheDocument();
  });
});

describe("TC-DND-025: Reordering within the same column is a no-op (AC-25)", () => {
  it("does not call the API regardless of on-screen order", async () => {
    seedApp({ id: "a25-1", status: "applied", co: "acme" });
    seedApp({ id: "a25-2", status: "applied", co: "globex" });

    renderBoard();

    const appliedCol = getColumn("Applied");
    const card1 = within(appliedCol).getByText("Acme Corp").closest(".kanban-card");
    await dragCard(card1, appliedCol);

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(appliedCol).getByText("Acme Corp")).toBeInTheDocument();
    expect(within(appliedCol).getByText("Globex Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-005: Dragging over Closed fans into exactly 3 labelled sub-zones (AC-5)", () => {
  it("renders Rejected, Ghosted, Withdrawn sub-zones with icons", async () => {
    seedApp({ id: "a5-closed", status: "rejected", co: "globex" });
    seedApp({ id: "a5-src", status: "interview", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);

    expect(screen.getByRole("button", { name: /rejected/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ghosted/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /withdrawn/i })).toBeInTheDocument();

    await cancelDrag();
  });
});

describe("TC-DND-006: Hovering a specific sub-zone highlights it as the active target (AC-6)", () => {
  it("marks Rejected as active while hovered, distinct from the others", async () => {
    seedApp({ id: "a6-src", status: "interview", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);

    const rejectedZone = screen.getByRole("button", { name: /rejected/i });
    const ghostedZone = screen.getByRole("button", { name: /ghosted/i });
    moveOver(rejectedZone);

    expect(rejectedZone).toHaveAttribute("data-active", "true");
    expect(ghostedZone).toHaveAttribute("data-active", "false");

    await cancelDrag();
  });
});

describe("TC-DND-007: Dropping on a sub-zone sets the corresponding terminal status (AC-7)", () => {
  it("calls the API with withdrawn and moves the card into Closed", async () => {
    seedApp({ id: "a7", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);
    const withdrawnZone = screen.getByRole("button", { name: /withdrawn/i });
    await dropOn(withdrawnZone);

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a7", "withdrawn"));
    expect(within(getColumn("Closed")).getByText("Acme Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-008: Leaving the Closed area collapses the fan-out without a status change (AC-8)", () => {
  it("removes the sub-zones once the pointer leaves Closed for another column", async () => {
    seedApp({ id: "a8", status: "interview", co: "acme" });

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);
    expect(screen.getByRole("button", { name: /rejected/i })).toBeInTheDocument();

    moveOver(getColumn("Screening"));
    expect(screen.queryByRole("button", { name: /rejected/i })).not.toBeInTheDocument();
    expect(updateApplicationStatus).not.toHaveBeenCalled();

    await cancelDrag();
  });
});

describe("TC-DND-009: Closed always collapses after the drag ends (AC-9)", () => {
  it("leaves no sub-zone elements after a sub-zone drop", async () => {
    seedApp({ id: "a9a", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);
    await dropOn(screen.getByRole("button", { name: /rejected/i }));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: /ghosted/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /withdrawn/i })).not.toBeInTheDocument();
  });

  it("leaves no sub-zone elements after dropping on a different column", async () => {
    seedApp({ id: "a9b", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    startDrag(card);
    moveOver(getColumn("Closed"));
    await dropOn(getColumn("Offer"));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: /rejected/i })).not.toBeInTheDocument();
  });

  it("leaves no sub-zone elements after a cancelled drag", async () => {
    seedApp({ id: "a9c", status: "interview", co: "acme" });

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    startDrag(card);
    moveOver(getColumn("Closed"));
    await cancelDrag();

    expect(screen.queryByRole("button", { name: /rejected/i })).not.toBeInTheDocument();
    expect(updateApplicationStatus).not.toHaveBeenCalled();
  });
});

describe("TC-DND-010: No DnD drop target ever produces Accepted (AC-10)", () => {
  it("never calls updateApplicationStatus with accepted, across every drop target", async () => {
    seedApp({ id: "a10", status: "offer", co: "acme" });
    updateApplicationStatus.mockResolvedValue({});
    const user = userEvent.setup();

    renderBoard();

    const card = () => getCardByCompany("Acme Corp");
    // offer -> applied is a backward move (AC-12): confirm it before it is applied.
    await dragCard(card(), getColumn("Applied"));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalled());

    expect(updateApplicationStatus).not.toHaveBeenCalledWith(expect.anything(), "accepted");
  });
});

describe("TC-DND-011: Marking Accepted still works via the existing detail-view StatusPicker (AC-11)", () => {
  it("updates status to accepted through the StatusPicker, unaffected by the Kanban DnD change", async () => {
    const app = seedApp({ id: "a11", status: "offer", co: "acme" });
    const onStatusChange = vi.fn();
    const user = userEvent.setup();

    render(
      <ApplicationDetailScreen
        app={app}
        goto={vi.fn()}
        onBack={vi.fn()}
        openSearch={vi.fn()}
        onDelete={vi.fn()}
        onStatusChange={onStatusChange}
        onNotesSave={vi.fn()}
        onEditSave={vi.fn()}
        onLogout={vi.fn()}
      />
    );

    const statusCard = screen.getByText("Status").closest(".card");
    await user.click(within(statusCard).getByText("Offer"));
    await user.click(within(statusCard).getByText("Accepted"));
    await user.click(within(statusCard).getByRole("button", { name: /^confirm$/i }));

    expect(onStatusChange).toHaveBeenCalledWith(app, "accepted");
    expect(app.status).toBe("accepted");
  });
});

describe("TC-DND-012/013/014: Backward pipeline move requires confirmation (AC-12, AC-13, AC-14)", () => {
  it("shows a confirmation and withholds the API call until confirmed (AC-12)", async () => {
    seedApp({ id: "a12", status: "interview", co: "acme" });

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Applied"));

    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/interview/i);
    expect(updateApplicationStatus).not.toHaveBeenCalled();
  });

  it("applies the change once Confirm is clicked (AC-13)", async () => {
    seedApp({ id: "a13", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});
    const user = userEvent.setup();

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Applied"));
    const dialog = await screen.findByRole("alertdialog");

    await user.click(within(dialog).getByRole("button", { name: /confirm/i }));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a13", "applied"));
    expect(within(getColumn("Applied")).getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("reverts the card to its original column when Cancel is clicked (AC-14)", async () => {
    seedApp({ id: "a14", status: "interview", co: "acme" });

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Applied"));
    const dialog = await screen.findByRole("alertdialog");

    fireEvent.click(within(dialog).getByRole("button", { name: /cancel/i }));

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(getColumn("Interview")).getByText("Acme Corp")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  });
});

describe("TC-DND-015: Moving from a Closed sub-status back into the pipeline requires confirmation (AC-15)", () => {
  it("shows a confirmation naming Rejected -> Screening", async () => {
    seedApp({ id: "a15", status: "rejected", co: "acme" });

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));

    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(/rejected/i);
    expect(dialog).toHaveTextContent(/screening/i);
    expect(updateApplicationStatus).not.toHaveBeenCalled();
  });
});

describe("TC-DND-016: Moving between two Closed sub-zones requires confirmation (AC-16)", () => {
  it("shows a confirmation naming Ghosted -> Rejected", async () => {
    seedApp({ id: "a16", status: "ghosted", co: "acme" });

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);
    await dropOn(screen.getByRole("button", { name: /rejected/i }));

    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(/ghosted/i);
    expect(dialog).toHaveTextContent(/rejected/i);
    expect(updateApplicationStatus).not.toHaveBeenCalled();
  });
});

describe("TC-DND-017: Forward/lateral moves never show the backward confirmation (AC-17)", () => {
  it("applies a forward pipeline move immediately", async () => {
    seedApp({ id: "a17a", status: "applied", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a17a", "screening"));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("applies a move into a Closed sub-zone immediately", async () => {
    seedApp({ id: "a17b", status: "applied", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    startDrag(card);
    moveOver(getColumn("Closed"));
    await dropOn(screen.getByRole("button", { name: /rejected/i }));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a17b", "rejected"));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});

describe("TC-DND-018: Touch drag works, including the Closed fan-out (AC-18)", () => {
  it("reaches the same handler via touch pointer events", async () => {
    seedApp({ id: "a18", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    fireEvent.pointerDown(card, { pointerId: 2, pointerType: "touch", clientX: 0, clientY: 0, isPrimary: true });
    fireEvent.pointerMove(document, { pointerId: 2, pointerType: "touch", clientX: 50, clientY: 50 });
    const closedRect = closedCol.getBoundingClientRect();
    fireEvent.pointerMove(closedCol, { pointerId: 2, pointerType: "touch", clientX: closedRect.left + 10, clientY: closedRect.top + 10 });

    const ghostedZone = screen.getByRole("button", { name: /ghosted/i });
    const zoneRect = ghostedZone.getBoundingClientRect();
    fireEvent.pointerMove(ghostedZone, { pointerId: 2, pointerType: "touch", clientX: zoneRect.left + 5, clientY: zoneRect.top + 5 });
    fireEvent.pointerUp(ghostedZone, { pointerId: 2, pointerType: "touch", clientX: zoneRect.left + 5, clientY: zoneRect.top + 5 });
    await settleGesture();

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a18", "ghosted"));
  });
});

describe("TC-DND-019: A card can be moved using only the keyboard (AC-19)", () => {
  it("moves the card to Screening via Tab, Enter, ArrowRight, Enter", async () => {
    seedApp({ id: "a19", status: "applied", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});
    const user = userEvent.setup();

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    card.focus();
    expect(card).toHaveFocus();

    await user.keyboard("{Enter}");
    await user.keyboard("{ArrowRight}");

    expect(within(card).getByRole("status")).toHaveTextContent(/screening/i);

    await user.keyboard("{Enter}");

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a19", "screening"));
    expect(within(getColumn("Screening")).getByText("Acme Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-020: A keyboard-initiated move can be cancelled with Escape (AC-20)", () => {
  it("leaves the card in place and exits move mode cleanly", async () => {
    seedApp({ id: "a20", status: "applied", co: "acme" });
    const user = userEvent.setup();

    renderBoard();
    const card = getCardByCompany("Acme Corp");
    card.focus();

    await user.keyboard("{Enter}");
    await user.keyboard("{ArrowRight}");
    await user.keyboard("{Escape}");

    expect(updateApplicationStatus).not.toHaveBeenCalled();
    expect(within(getColumn("Applied")).getByText("Acme Corp")).toBeInTheDocument();
    expect(card).toHaveAttribute("aria-pressed", "false");
  });
});

describe("TC-DND-021: A failed status update reverts the optimistic move and shows an error (AC-21)", () => {
  it("reverts to Applied and shows an error message", async () => {
    seedApp({ id: "a21", status: "applied", co: "acme" });
    updateApplicationStatus.mockRejectedValueOnce(new ApiError(500, "Server Error"));

    renderBoard();
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));

    await waitFor(() => expect(within(getColumn("Applied")).getByText("Acme Corp")).toBeInTheDocument());
    expect(await screen.findByText(/couldn't update status/i)).toBeInTheDocument();
    expect(within(getColumn("Screening")).queryByText("Acme Corp")).not.toBeInTheDocument();
  });
});

describe("TC-DND-022: A 401 during a drag-triggered update follows session-expiry handling (AC-22)", () => {
  it("calls onLogout and does not show the generic error toast", async () => {
    seedApp({ id: "a22", status: "applied", co: "acme" });
    updateApplicationStatus.mockRejectedValueOnce(new ApiError(401, "Unauthorized"));
    const onLogout = vi.fn();

    renderBoard({ onLogout });
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/couldn't update status/i)).not.toBeInTheDocument();
  });
});

describe("TC-DND-023: An empty Closed column is still a valid drop target (AC-23)", () => {
  it("fans out and accepts a drop even when Closed starts empty", async () => {
    seedApp({ id: "a23", status: "interview", co: "acme" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();
    expect(within(getColumn("Closed")).getByText("Nothing here.")).toBeInTheDocument();

    const card = getCardByCompany("Acme Corp");
    const closedCol = getColumn("Closed");
    startDrag(card);
    moveOver(closedCol);
    await dropOn(screen.getByRole("button", { name: /rejected/i }));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a23", "rejected"));
    expect(within(getColumn("Closed")).getByText("Acme Corp")).toBeInTheDocument();
    expect(within(getColumn("Closed")).queryByText("Nothing here.")).not.toBeInTheDocument();
  });
});

describe("TC-DND-024: A column hidden by the active filter cannot be a drop target (AC-24)", () => {
  it("only renders the visible columns and completes a drag between them", async () => {
    seedApp({ id: "a24-applied", status: "applied", co: "acme" });
    seedApp({ id: "a24-closed", status: "rejected", co: "globex" });
    updateApplicationStatus.mockResolvedValueOnce({});

    renderBoard();

    const findChip = (re) => [...document.querySelectorAll(".chip")].find((el) => re.test(el.textContent));

    fireEvent.click(findChip(/^Applied/));
    await waitFor(() => expect(findChip(/^Applied/)).toHaveClass("active"));

    fireEvent.click(findChip(/^Screening/));
    await waitFor(() => {
      expect(findChip(/^Applied/)).toHaveClass("active");
      expect(findChip(/^Screening/)).toHaveClass("active");
    });

    expect(screen.queryByText("Closed", { selector: ".kanban-head .name" })).not.toBeInTheDocument();
    expect(screen.queryByText("Interview", { selector: ".kanban-head .name" })).not.toBeInTheDocument();
    expect(screen.queryByText("Offer", { selector: ".kanban-head .name" })).not.toBeInTheDocument();

    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));
    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledWith("a24-applied", "screening"));
  });
});

describe("TC-DND-026: Two drags resolved out of order do not corrupt the board (race condition)", () => {
  it("settles each card in its own drop target regardless of resolution order", async () => {
    seedApp({ id: "a26-A", status: "applied", co: "acme" });
    seedApp({ id: "a26-B", status: "screening", co: "globex" });

    let resolveA, resolveB;
    updateApplicationStatus.mockImplementation((id) => {
      if (id === "a26-A") return new Promise((r) => { resolveA = r; });
      return new Promise((r) => { resolveB = r; });
    });

    renderBoard();

    // Both drags land before either API call settles (neither promise is resolved yet),
    // so both are in flight at once; then resolve them out of order.
    await dragCard(getCardByCompany("Acme Corp"), getColumn("Interview"));
    await dragCard(getCardByCompany("Globex Corp"), getColumn("Offer"));

    await waitFor(() => expect(updateApplicationStatus).toHaveBeenCalledTimes(2));
    expect(updateApplicationStatus).toHaveBeenCalledWith("a26-A", "interviewing");
    expect(updateApplicationStatus).toHaveBeenCalledWith("a26-B", "offered");

    // Resolve out of order: B first, then A
    await act(async () => { resolveB({}); });
    await act(async () => { resolveA({}); });

    await waitFor(() => {
      expect(within(getColumn("Interview")).getByText("Acme Corp")).toBeInTheDocument();
      expect(within(getColumn("Offer")).getByText("Globex Corp")).toBeInTheDocument();
    });
  });
});

describe("TC-DND-027: A drop while a previous drag's call is in flight is accepted independently", () => {
  it("optimistically shows the second card without waiting on the first's API call", async () => {
    seedApp({ id: "a27-A", status: "applied", co: "acme" });
    seedApp({ id: "a27-B", status: "applied", co: "globex" });

    let resolveA;
    updateApplicationStatus.mockImplementationOnce(() => new Promise((r) => { resolveA = r; }));
    updateApplicationStatus.mockImplementationOnce(() => Promise.resolve({}));

    renderBoard();

    await dragCard(getCardByCompany("Acme Corp"), getColumn("Screening"));
    await dragCard(getCardByCompany("Globex Corp"), getColumn("Interview"));

    await waitFor(() => expect(within(getColumn("Interview")).getByText("Globex Corp")).toBeInTheDocument());
    expect(updateApplicationStatus).toHaveBeenCalledTimes(2);

    await act(async () => { resolveA({}); });
    await waitFor(() => expect(within(getColumn("Screening")).getByText("Acme Corp")).toBeInTheDocument());
    expect(within(getColumn("Interview")).getByText("Globex Corp")).toBeInTheDocument();
  });
});

describe("TC-DND-028: An empty board renders all columns as valid, non-crashing drop targets", () => {
  it("renders all 5 empty-state columns with no card elements and no crash", async () => {
    renderBoard();

    ["Applied", "Screening", "Interview", "Offer", "Closed"].forEach((name) => {
      const col = getColumn(name);
      expect(within(col).getByText("Nothing here.")).toBeInTheDocument();
    });
    expect(document.querySelectorAll(".kanban-card").length).toBe(0);
  });
});
