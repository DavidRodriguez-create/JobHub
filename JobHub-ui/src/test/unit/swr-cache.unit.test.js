/**
 * Unit tests for the SWR session cache in notifications.js.
 * Story #136 / Sub-issue #141 — Speed up notification settings page load.
 *
 * TC-C3: fresh module import issues a GET (no stale cross-session cache).
 *
 * Design note: the SWR cache is stored in module-level state inside
 * notifications.js. vi.resetModules() + dynamic re-import simulates a fresh
 * browser session (new module execution). The cache must be purely in-memory
 * and NOT persisted to localStorage or sessionStorage.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

describe("TC-C3: SWR cache does not persist across sessions (module boundaries)", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("TC-C3: a fresh module import always calls the underlying request (no stale cache hit)", async () => {
    const mockRequest = vi.fn().mockResolvedValue({
      data: {
        weeklyDigestEmail: true,
        inAppNotificationsEnabled: false,
        interviewReminders: true,
        ghostedAlert: true,
      },
      total: null,
      status: 200,
    });

    vi.doMock("../../api/client.js", () => ({ request: mockRequest }));

    // Fresh import simulates a new browser session (module re-execution clears cache)
    const { getNotificationPreferences } = await import("../../api/notifications.js");

    const result = await getNotificationPreferences();

    expect(mockRequest).toHaveBeenCalledTimes(1);
    const [path, opts] = mockRequest.mock.calls[0];
    expect(path).toBe("/notifications/preferences");
    expect(opts.auth).toBe(true);
    expect(result.weeklyDigestEmail).toBe(true);
  });

  it("TC-C3b: within same module scope, second call returns cached value without calling request again", async () => {
    const mockRequest = vi.fn().mockResolvedValue({
      data: {
        weeklyDigestEmail: false,
        inAppNotificationsEnabled: true,
        interviewReminders: false,
        ghostedAlert: false,
      },
      total: null,
      status: 200,
    });

    vi.doMock("../../api/client.js", () => ({ request: mockRequest }));

    const { getNotificationPreferences } = await import("../../api/notifications.js");

    // First call: network
    const result1 = await getNotificationPreferences();
    // Second call: cache hit
    const result2 = await getNotificationPreferences();

    expect(mockRequest).toHaveBeenCalledTimes(1);
    expect(result1).toEqual(result2);
  });

  it("TC-C3c: error response is NOT cached; next call hits the network again", async () => {
    const successData = {
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: false,
      interviewReminders: true,
      ghostedAlert: true,
    };

    const mockRequest = vi
      .fn()
      .mockRejectedValueOnce(new Error("HTTP 500"))
      .mockResolvedValueOnce({ data: successData, total: null, status: 200 });

    vi.doMock("../../api/client.js", () => ({ request: mockRequest }));

    const { getNotificationPreferences } = await import("../../api/notifications.js");

    // First call: error (must not be cached)
    await expect(getNotificationPreferences()).rejects.toThrow();

    // Second call: fresh network request
    const result = await getNotificationPreferences();
    expect(mockRequest).toHaveBeenCalledTimes(2);
    expect(result).toEqual(successData);
  });
});
