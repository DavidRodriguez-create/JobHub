/**
 * Component tests for CommandPalette's settings/navigation index.
 * Story #304 (sub-issue #308). Strategy A cases from
 * JobHub-ui/docs/testing/304-settings-command-palette-cases.md.
 *
 * TC-304-01, 02        AC-1  empty query shows full catalogue (non-admin/admin)
 * TC-304-03..12        AC-2..5, BR-3 keyword/alias/case/trim matching
 * TC-304-17, 18        AC-9  admin gating (non-admin, typed + empty query)
 * TC-304-20            AC-10 no-match empty state
 * TC-304-21..25        AC-11 regression: jobs/applications search unaffected
 * TC-304-27..29        AC-13 keyboard navigation over settings results
 * TC-304-31            edge: "admin" indistinguishable from any other no-match
 * TC-304-33            edge: non-settings mode unaffected (⌘K path, same as topbar)
 * TC-304-34            BR-8  placeholder/hint text swap
 */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../data/mockData.js", () => {
  const companies = {
    acme: { name: "Acme", industry: "Tech", size: "50-100", hq: "Remote", url: "" },
  };
  const jobs = [
    { id: "J-1", co: "acme", title: "Frontend Engineer", location: "Remote", comp: "$120k", tags: ["react"] },
  ];
  const applications = [
    { id: "APP-1", jobId: "J-1", status: "applied" },
  ];
  return {
    default: {
      companies,
      jobs,
      applications,
      saved: [],
      byId: (id) => jobs.find((j) => j.id === id),
      coOf: (co) => companies[co] || { name: "Acme", industry: "-", size: "-", hq: "-", url: "" },
    },
  };
});

import { CommandPalette } from "../../components/CommandPalette.jsx";

function renderPalette(props = {}) {
  return render(
    <CommandPalette
      mode="settings"
      onClose={vi.fn()}
      onSelectJob={vi.fn()}
      onSelectApp={vi.fn()}
      {...props}
    />
  );
}

const ALL_NON_ADMIN_LABELS = [
  "Account settings",
  "Change password",
  "Two-factor auth",
  "Notification preferences",
  "Sources & filters",
  "Integrations",
  "Billing",
  "Data & privacy",
];

describe("AC-1 : empty query in Settings mode shows the full catalogue", () => {
  it("TC-304-01 : non-admin sees all 8 entries in catalogue order, no job/app rows", () => {
    renderPalette({ isAdmin: false });

    const rows = screen.getAllByTestId("settings-result-row");
    expect(rows).toHaveLength(8);
    rows.forEach((row, i) => {
      expect(row).toHaveTextContent(ALL_NON_ADMIN_LABELS[i]);
    });

    expect(screen.queryByText("Frontend Engineer")).not.toBeInTheDocument();
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();
  });

  it("TC-304-02 : admin sees all 9 entries including Admin panel as the 9th", () => {
    renderPalette({ isAdmin: true });

    const rows = screen.getAllByTestId("settings-result-row");
    expect(rows).toHaveLength(9);
    expect(rows[8]).toHaveTextContent("Admin panel");
  });
});

describe("AC-2..5 + BR-3 : keyword/alias matching", () => {
  it("TC-304-03 : typing \"password\" surfaces Change password, no job/app row", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "password" } });
    expect(screen.getByText("Change password")).toBeInTheDocument();
    expect(screen.queryByText("Frontend Engineer")).not.toBeInTheDocument();
  });

  it("TC-304-04 : typing \"notifications\" surfaces Notification preferences", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "notifications" } });
    expect(screen.getByText("Notification preferences")).toBeInTheDocument();
  });

  it("TC-304-05 : alias \"2fa\" surfaces Two-factor auth", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "2fa" } });
    expect(screen.getByText("Two-factor auth")).toBeInTheDocument();
  });

  it("TC-304-06 : alias \"privacy\" surfaces Data & privacy", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "privacy" } });
    expect(screen.getByText("Data & privacy")).toBeInTheDocument();
  });

  it("TC-304-07 : alias \"pwd\" surfaces Change password", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "pwd" } });
    expect(screen.getByText("Change password")).toBeInTheDocument();
  });

  it("TC-304-08 : alias \"totp\" surfaces Two-factor auth", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "totp" } });
    expect(screen.getByText("Two-factor auth")).toBeInTheDocument();
  });

  it("TC-304-09 : alias \"greenhouse\" surfaces Sources & filters", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "greenhouse" } });
    expect(screen.getByText("Sources & filters")).toBeInTheDocument();
  });

  it("TC-304-10 : substring \"notif\" surfaces Notification preferences", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "notif" } });
    expect(screen.getByText("Notification preferences")).toBeInTheDocument();
  });

  it("TC-304-11 : mixed-case \"PaSsWoRd\" still surfaces Change password", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "PaSsWoRd" } });
    expect(screen.getByText("Change password")).toBeInTheDocument();
  });

  it("TC-304-12 : leading/trailing whitespace is trimmed before matching", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "  password  " } });
    expect(screen.getByText("Change password")).toBeInTheDocument();
  });
});

describe("AC-9 : Admin panel entry absent for non-admins, regardless of query", () => {
  it("TC-304-17 : typing \"admin\" as non-admin yields the settings empty state, not Admin panel", () => {
    renderPalette({ isAdmin: false });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "admin" } });
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();
    expect(screen.getByText('No settings found for "admin"')).toBeInTheDocument();
  });

  it("TC-304-18 : empty query as non-admin shows exactly 8 entries, no Admin panel", () => {
    renderPalette({ isAdmin: false });
    const rows = screen.getAllByTestId("settings-result-row");
    expect(rows).toHaveLength(8);
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();
  });
});

describe("AC-10 : no-match query shows a settings-specific empty state", () => {
  it("TC-304-20 : \"xyz123\" shows settings empty-state copy, not jobs/applications copy", () => {
    renderPalette();
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "xyz123" } });
    expect(screen.getByText('No settings found for "xyz123"')).toBeInTheDocument();
    expect(screen.queryByText(/No jobs found/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No applications found/)).not.toBeInTheDocument();
  });
});

describe("Edge : \"admin\" typed by a non-admin is indistinguishable from any other no-match", () => {
  it("TC-304-31 : empty-state copy for \"admin\" matches the shape used for \"xyz123\"", () => {
    const { unmount } = renderPalette({ isAdmin: false });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "admin" } });
    expect(screen.getByText('No settings found for "admin"')).toBeInTheDocument();
    unmount();

    renderPalette({ isAdmin: false });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "xyz123" } });
    expect(screen.getByText('No settings found for "xyz123"')).toBeInTheDocument();
  });
});

describe("AC-11 : palette outside Settings mode is unaffected (regression)", () => {
  it("TC-304-21 : mode=\"search\" empty query shows jobs, Jobs hint/placeholder, no settings entries", () => {
    render(
      <CommandPalette mode="search" onClose={vi.fn()} onSelectJob={vi.fn()} onSelectApp={vi.fn()} />
    );
    expect(screen.getByText("Frontend Engineer")).toBeInTheDocument();
    expect(screen.getByText("Jobs")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Search jobs…")).toBeInTheDocument();
    expect(screen.queryByText("Change password")).not.toBeInTheDocument();
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();
  });

  it("TC-304-22 : mode=\"search\" matching query selects the job via onSelectJob + onClose", () => {
    const onSelectJob = vi.fn();
    const onClose = vi.fn();
    render(
      <CommandPalette mode="search" onClose={onClose} onSelectJob={onSelectJob} onSelectApp={vi.fn()} />
    );
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Frontend" } });
    fireEvent.click(screen.getByText("Frontend Engineer"));
    expect(onSelectJob).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("TC-304-23 : mode=\"applications\" empty query shows applications, Applications hint/placeholder", () => {
    render(
      <CommandPalette mode="applications" onClose={vi.fn()} onSelectJob={vi.fn()} onSelectApp={vi.fn()} />
    );
    expect(screen.getByText("Applications")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Search applications…")).toBeInTheDocument();
    expect(screen.queryByText("Change password")).not.toBeInTheDocument();
  });

  it("TC-304-24 : mode=\"applications\" matching query selects via onSelectApp + onClose", () => {
    const onSelectApp = vi.fn();
    const onClose = vi.fn();
    render(
      <CommandPalette mode="applications" onClose={onClose} onSelectJob={vi.fn()} onSelectApp={onSelectApp} />
    );
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Frontend" } });
    fireEvent.click(screen.getByText("Frontend Engineer"));
    expect(onSelectApp).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("TC-304-25 : mode=\"dashboard\" no-match query shows the existing applications empty state", () => {
    render(
      <CommandPalette mode="dashboard" onClose={vi.fn()} onSelectJob={vi.fn()} onSelectApp={vi.fn()} />
    );
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "xyz123" } });
    expect(screen.getByText('No applications found for "xyz123"')).toBeInTheDocument();
    expect(screen.queryByText(/No settings found/)).not.toBeInTheDocument();
  });

  it("TC-304-33 : any non-settings mode is unaffected regardless of the trigger path (⌘K or topbar)", () => {
    render(
      <CommandPalette mode="dashboard" onClose={vi.fn()} onSelectJob={vi.fn()} onSelectApp={vi.fn()} />
    );
    expect(screen.getByText("Applications")).toBeInTheDocument();
    expect(screen.queryByText("Change password")).not.toBeInTheDocument();
  });
});

describe("AC-13 : keyboard navigation works identically over settings results", () => {
  it("TC-304-27 : ArrowDown twice then Enter selects the entry at index 2", () => {
    const onSelectSettings = vi.fn();
    renderPalette({ onSelectSettings });
    const input = screen.getByRole("textbox");
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSelectSettings).toHaveBeenCalledTimes(1);
    const entry = onSelectSettings.mock.calls[0][0];
    expect(entry.label).toBe(ALL_NON_ADMIN_LABELS[2]);
  });

  it("TC-304-28 : ArrowUp at index 0 stays clamped at 0", () => {
    const onSelectSettings = vi.fn();
    renderPalette({ onSelectSettings });
    const input = screen.getByRole("textbox");
    fireEvent.keyDown(input, { key: "ArrowUp" });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSelectSettings).toHaveBeenCalledTimes(1);
    const entry = onSelectSettings.mock.calls[0][0];
    expect(entry.label).toBe(ALL_NON_ADMIN_LABELS[0]);
  });

  it("TC-304-29 : narrowing the query resets the highlighted index to 0", () => {
    const onSelectSettings = vi.fn();
    renderPalette({ onSelectSettings });
    const input = screen.getByRole("textbox");
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.change(input, { target: { value: "password" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSelectSettings).toHaveBeenCalledTimes(1);
    const entry = onSelectSettings.mock.calls[0][0];
    expect(entry.label).toBe("Change password");
  });
});

describe("BR-8 : placeholder / hint text while in Settings mode", () => {
  it("TC-304-34 : placeholder reads \"Search settings…\" and hint reads \"Settings\"", () => {
    renderPalette();
    expect(screen.getByPlaceholderText("Search settings…")).toBeInTheDocument();
    expect(screen.getByText("Settings")).toBeInTheDocument();
    expect(screen.queryByText("Jobs")).not.toBeInTheDocument();
    expect(screen.queryByText("Applications")).not.toBeInTheDocument();
  });
});
