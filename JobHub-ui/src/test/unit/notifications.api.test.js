/**
 * Unit tests for src/api/notifications.js — notification preferences API client.
 * Story #78 / Ticket #87 — Wire Settings notification toggles to notification-service.
 * Story #244 / Ticket #260 — UI244-API-01..02: companyLogoUrl contract alignment.
 *
 * Contract: api-contracts/src/main/resources/openapi/notification-service.yaml
 *   GET  /notifications/preferences -> NotificationPreferencesResponse
 *   PUT  /notifications/preferences -> NotificationPreferencesResponse (200)
 *   GET  /notifications -> NotificationResponse[] (includes companyLogoUrl, nullable uri)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
}));

const { request } = await import("../../api/client.js");
const { getNotificationPreferences, updateNotificationPreferences, deleteNotification, listNotifications } = await import("../../api/notifications.js");

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("getNotificationPreferences", () => {
  it("calls GET /notifications/preferences with auth and returns the response body", async () => {
    const body = {
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: false,
      interviewReminders: true,
      ghostedAlert: true,
    };
    request.mockResolvedValueOnce({ data: body, total: null, status: 200 });

    const result = await getNotificationPreferences();

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/notifications/preferences");
    expect(opts.method).toBeUndefined(); // default GET
    expect(opts.auth).toBe(true);
    expect(result).toEqual(body);
  });
});

describe("updateNotificationPreferences", () => {
  it("calls PUT /notifications/preferences with auth and only the supplied fields", async () => {
    const body = {
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: true,
      interviewReminders: true,
      ghostedAlert: true,
    };
    request.mockResolvedValueOnce({ data: body, total: null, status: 200 });

    const result = await updateNotificationPreferences({ inAppNotificationsEnabled: true });

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/notifications/preferences");
    expect(opts.method).toBe("PUT");
    expect(opts.auth).toBe(true);
    expect(opts.body).toEqual({ inAppNotificationsEnabled: true });
    expect(result).toEqual(body);
  });

  it("sends an empty body when called with no arguments (PUT {} is valid per BR-3)", async () => {
    const body = {
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: false,
      interviewReminders: true,
      ghostedAlert: true,
    };
    request.mockResolvedValueOnce({ data: body, total: null, status: 200 });

    await updateNotificationPreferences();

    const [, opts] = request.mock.calls[0];
    expect(opts.body).toEqual({});
  });

  it("supports sending all four fields for a full update (AC-3)", async () => {
    const full = {
      weeklyDigestEmail: false,
      inAppNotificationsEnabled: true,
      interviewReminders: false,
      ghostedAlert: false,
    };
    request.mockResolvedValueOnce({ data: full, total: null, status: 200 });

    await updateNotificationPreferences(full);

    const [, opts] = request.mock.calls[0];
    expect(opts.body).toEqual(full);
  });

  it("propagates ApiError on failure (e.g. 500/401) for the caller to handle", async () => {
    const err = new Error("HTTP 500");
    err.status = 500;
    request.mockRejectedValueOnce(err);

    await expect(updateNotificationPreferences({ ghostedAlert: false })).rejects.toThrow();
  });
});

// Story #206 / Ticket #234: DELETE /notifications/{id} client function.
describe("deleteNotification", () => {
  it("TC-206-F-01: calls DELETE /notifications/{id} with auth and resolves on 204", async () => {
    request.mockResolvedValueOnce({ data: undefined, total: null, status: 204 });

    await expect(deleteNotification("n-1")).resolves.toBeUndefined();

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/notifications/n-1");
    expect(opts.method).toBe("DELETE");
    expect(opts.auth).toBe(true);
  });

  it("TC-206-F-02: propagates a 404 ApiError to the caller", async () => {
    const err = new Error("Not Found");
    err.status = 404;
    request.mockRejectedValueOnce(err);

    await expect(deleteNotification("n-1")).rejects.toThrow();
  });

  it("TC-206-F-03: propagates a 500 ApiError to the caller without special-casing the status", async () => {
    const err = new Error("Internal Server Error");
    err.status = 500;
    request.mockRejectedValueOnce(err);

    await expect(deleteNotification("n-1")).rejects.toThrow();
  });
});

// ─── Story #244 / Ticket #260: companyLogoUrl contract alignment (UI244-API-*) ───

describe("listNotifications - companyLogoUrl contract alignment (UI244-API-01..02)", () => {
  it("UI244-API-01: listNotifications passes companyLogoUrl through unmodified when present in the API response", async () => {
    const logoUrl = "https://cdn.example/acme.png";
    const responseBody = {
      content: [
        {
          id: "n-1",
          type: "APPLICATION_UPDATE",
          title: "Moved",
          message: "Status changed.",
          read: false,
          createdAt: new Date().toISOString(),
          applicationId: "app-1",
          company: "Acme Corp",
          jobTitle: "Engineer",
          companyLogoUrl: logoUrl,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    request.mockResolvedValueOnce({ data: responseBody, total: null, status: 200 });

    const result = await listNotifications();

    expect(result.content[0].companyLogoUrl).toBe(logoUrl);
    // Other existing fields unaffected (regression)
    expect(result.content[0].company).toBe("Acme Corp");
    expect(result.content[0].jobTitle).toBe("Engineer");
  });

  it("UI244-API-02: listNotifications resolves without throwing when the response item omits companyLogoUrl (pre-deploy backend shape)", async () => {
    const responseBody = {
      content: [
        {
          id: "n-1",
          type: "APPLICATION_UPDATE",
          title: "Moved",
          message: "Status changed.",
          read: false,
          createdAt: new Date().toISOString(),
          applicationId: "app-1",
          company: "Acme Corp",
          jobTitle: "Engineer",
          // companyLogoUrl intentionally omitted
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    request.mockResolvedValueOnce({ data: responseBody, total: null, status: 200 });

    // Must not throw
    const result = await listNotifications();

    // No companyLogoUrl key on the item - that's fine; presentation layer handles it
    expect(result.content[0].companyLogoUrl).toBeUndefined();
    expect(result.content[0].company).toBe("Acme Corp");
  });
});
