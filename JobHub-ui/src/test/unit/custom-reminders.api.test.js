/**
 * Unit tests for src/api/custom-reminders.js
 * Contract: notification-service.yaml (story #134, frozen; story #175 / ADR 0013 query-param fold)
 *
 * Cases:
 *   CR-UI-001: createCustomReminder -> POST /notifications/custom-reminders
 *   CR-UI-002: getCustomReminder -> GET /notifications/custom-reminders/{id}
 *   CR-UI-003: updateCustomReminder -> PUT /notifications/custom-reminders/{id}
 *   CR-UI-004: deleteCustomReminder -> DELETE /notifications/custom-reminders/{id}
 *   CR-UI-005: listMyCustomReminders() -> no includeFired param
 *   CR-UI-006: listMyCustomReminders({includeFired: true}) -> includes param
 *   CR-UI-U07a: listCustomRemindersByApplication -> GET /notifications/custom-reminders?applicationId=<id>
 *   CR-UI-U07b: listCustomRemindersByApplication with includeFired=true adds the param
 *   CR-UI-U07c: old /applications/{id}/custom-reminders path is never constructed
 *   CR-UI-U07d: applicationId (UUID) appears verbatim, not mangled/double-encoded
 *   CR-UI-008: all functions propagate ApiError on 4xx/5xx
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
}));

const { request } = await import("../../api/client.js");
const {
  createCustomReminder,
  getCustomReminder,
  updateCustomReminder,
  deleteCustomReminder,
  listMyCustomReminders,
  listCustomRemindersByApplication,
} = await import("../../api/custom-reminders.js");

const REMINDER_RESPONSE = {
  id: "er000000-0000-0000-0000-000000000001",
  applicationId: "ea000000-0000-0000-0000-000000000001",
  title: "Prep notes",
  triggerAtUtc: "2026-07-01T14:00:00Z",
  channels: ["IN_APP"],
  status: "SCHEDULED",
  createdAt: "2026-06-20T10:00:00Z",
  updatedAt: "2026-06-20T10:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CR-UI-001: createCustomReminder", () => {
  it("calls POST /notifications/custom-reminders with auth and returns 201 body", async () => {
    request.mockResolvedValueOnce({ data: REMINDER_RESPONSE, total: null, status: 201 });

    const input = {
      applicationId: "ea000000-0000-0000-0000-000000000001",
      title: "Prep notes",
      triggerAtUtc: "2026-07-01T14:00:00Z",
      channels: ["IN_APP"],
    };
    const result = await createCustomReminder(input);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/notifications/custom-reminders");
    expect(opts.method).toBe("POST");
    expect(opts.auth).toBe(true);
    expect(opts.body).toEqual(input);
    expect(result).toEqual(REMINDER_RESPONSE);
  });
});

describe("CR-UI-002: getCustomReminder", () => {
  it("calls GET /notifications/custom-reminders/{id} with auth and returns data", async () => {
    request.mockResolvedValueOnce({ data: REMINDER_RESPONSE, total: null, status: 200 });

    const id = "er000000-0000-0000-0000-000000000001";
    const result = await getCustomReminder(id);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe(`/notifications/custom-reminders/${id}`);
    expect(opts.method).toBeUndefined();
    expect(opts.auth).toBe(true);
    expect(result).toEqual(REMINDER_RESPONSE);
  });
});

describe("CR-UI-003: updateCustomReminder", () => {
  it("calls PUT /notifications/custom-reminders/{id} with auth and partial body", async () => {
    const updated = { ...REMINDER_RESPONSE, title: "New title" };
    request.mockResolvedValueOnce({ data: updated, total: null, status: 200 });

    const id = "er000000-0000-0000-0000-000000000001";
    const patch = { title: "New title" };
    const result = await updateCustomReminder(id, patch);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe(`/notifications/custom-reminders/${id}`);
    expect(opts.method).toBe("PUT");
    expect(opts.auth).toBe(true);
    expect(opts.body).toEqual(patch);
    expect(result).toEqual(updated);
  });
});

describe("CR-UI-004: deleteCustomReminder", () => {
  it("calls DELETE /notifications/custom-reminders/{id} with auth; no body returned on 204", async () => {
    request.mockResolvedValueOnce({ data: null, total: null, status: 204 });

    const id = "er000000-0000-0000-0000-000000000001";
    await deleteCustomReminder(id);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe(`/notifications/custom-reminders/${id}`);
    expect(opts.method).toBe("DELETE");
    expect(opts.auth).toBe(true);
  });
});

describe("CR-UI-005: listMyCustomReminders (default)", () => {
  it("calls GET /notifications/custom-reminders without includeFired by default", async () => {
    request.mockResolvedValueOnce({ data: { content: [] }, total: null, status: 200 });

    const result = await listMyCustomReminders();

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/notifications/custom-reminders");
    expect(opts.auth).toBe(true);
    expect(result).toEqual({ content: [] });
  });
});

describe("CR-UI-006: listMyCustomReminders with includeFired=true", () => {
  it("includes includeFired=true in the request URL", async () => {
    request.mockResolvedValueOnce({ data: { content: [] }, total: null, status: 200 });

    await listMyCustomReminders({ includeFired: true });

    const [path] = request.mock.calls[0];
    expect(path).toContain("includeFired=true");
  });
});

describe("CR-UI-U07a: listCustomRemindersByApplication, no includeFired", () => {
  it("calls GET /notifications/custom-reminders?applicationId=<id> with auth, GET, and returns .data unchanged", async () => {
    request.mockResolvedValueOnce({ data: { content: [REMINDER_RESPONSE] }, total: null, status: 200 });

    const appId = "ea000000-0000-0000-0000-000000000001";
    const result = await listCustomRemindersByApplication(appId);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe(`/notifications/custom-reminders?applicationId=${appId}`);
    expect(opts.auth).toBe(true);
    expect(opts.method).toBeUndefined();
    expect(result).toEqual({ content: [REMINDER_RESPONSE] });
  });
});

describe("CR-UI-U07b: listCustomRemindersByApplication, includeFired=true adds the param", () => {
  it("includes both applicationId and includeFired=true in the query string", async () => {
    request.mockResolvedValueOnce({ data: { content: [] }, total: null, status: 200 });

    const appId = "ea000000-0000-0000-0000-000000000001";
    await listCustomRemindersByApplication(appId, { includeFired: true });

    const [path] = request.mock.calls[0];
    expect(path).toContain(`applicationId=${appId}`);
    expect(path).toContain("includeFired=true");
  });
});

describe("CR-UI-U07c: old /applications/{id}/custom-reminders path is never constructed", () => {
  it("does not start with /applications/ and does not match /applications/.+/custom-reminders", async () => {
    request.mockResolvedValueOnce({ data: { content: [] }, total: null, status: 200 });

    const appId = "ea000000-0000-0000-0000-000000000001";
    await listCustomRemindersByApplication(appId, { includeFired: true });

    const [path] = request.mock.calls[0];
    expect(path.startsWith("/applications/")).toBe(false);
    expect(path).not.toMatch(/^\/applications\/.+\/custom-reminders/);
  });
});

describe("CR-UI-U07d: applicationId is not mangled or double-encoded", () => {
  it("includes the UUID verbatim in the query string", async () => {
    request.mockResolvedValueOnce({ data: { content: [] }, total: null, status: 200 });

    const appId = "ea000000-0000-0000-0000-000000000001";
    await listCustomRemindersByApplication(appId);

    const [path] = request.mock.calls[0];
    expect(path).toContain(`applicationId=${appId}`);
    expect(path).not.toContain("%");
  });
});

describe("CR-UI-008: error propagation", () => {
  it("propagates ApiError on 400 from createCustomReminder", async () => {
    const err = Object.assign(new Error("Bad Request"), { status: 400 });
    request.mockRejectedValueOnce(err);

    await expect(
      createCustomReminder({ title: "", channels: [], triggerAtUtc: "past", applicationId: "x" })
    ).rejects.toMatchObject({ status: 400 });
  });

  it("propagates ApiError on 404 from getCustomReminder", async () => {
    const err = Object.assign(new Error("Not Found"), { status: 404 });
    request.mockRejectedValueOnce(err);

    await expect(getCustomReminder("missing-id")).rejects.toMatchObject({ status: 404 });
  });

  it("propagates ApiError on 409 from updateCustomReminder", async () => {
    const err = Object.assign(new Error("Conflict"), { status: 409 });
    request.mockRejectedValueOnce(err);

    await expect(updateCustomReminder("id", { title: "x" })).rejects.toMatchObject({ status: 409 });
  });

  it("propagates ApiError on 404 from deleteCustomReminder (stale-list race)", async () => {
    const err = Object.assign(new Error("Not Found"), { status: 404 });
    request.mockRejectedValueOnce(err);

    await expect(deleteCustomReminder("gone-id")).rejects.toMatchObject({ status: 404 });
  });
});
