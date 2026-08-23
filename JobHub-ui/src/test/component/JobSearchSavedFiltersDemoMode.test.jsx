/**
 * Component tests for per-user saved filters in demo/offline mode, story #523.
 * Cases: TC-523-J01..J03 (docs/qa/523-comp-filter-removal-and-per-user-saved-filters-test-cases.md).
 * USE_API=false: presets live in component state for the session, no network calls.
 */
import React from "react";
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("../../api/config.js", () => ({ USE_API: false }));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn(),
  getJobFacets: vi.fn(),
  listSavedFilters: vi.fn(),
  createSavedFilter: vi.fn(),
  deleteSavedFilter: vi.fn(),
  peekSearch: vi.fn(),
  prefetchSearch: vi.fn(),
  peekFacets: vi.fn(),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "—", industry: "—", size: "—", hq: "—", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/WritingLoader.jsx", () => ({
  default: ({ label }) => <div data-testid="writing-loader">{label}</div>,
}));

vi.mock("../../components/RichText.jsx", () => ({
  default: ({ text }) => <div>{text}</div>,
}));

const { listSavedFilters, createSavedFilter, deleteSavedFilter } = await import("../../api/jobs.js");
const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");

const DEFAULT_PROPS = {
  goto: vi.fn(),
  onSaveToggle: vi.fn(),
  savedIds: new Set(),
  openJob: vi.fn(),
  appliedJobIds: new Set(),
  authed: true,
  openSearch: vi.fn(),
};

async function renderMock(props = {}) {
  vi.clearAllMocks();
  return render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
}

async function openSaveDialog() {
  const btn = await waitFor(() => screen.getByText("Save filter"));
  fireEvent.click(btn);
  return screen.getByPlaceholderText("Filter name…");
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// TC-523-J01
it("TC-523-J01 save, apply, and delete all work purely in component state, no network calls", async () => {
  await renderMock();

  const input = screen.getByPlaceholderText("Title, company…");
  fireEvent.change(input, { target: { value: "designer" } });

  const nameInput = await openSaveDialog();
  fireEvent.change(nameInput, { target: { value: "Design roles" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  expect(await screen.findByText("Saved filters")).toBeInTheDocument();

  fireEvent.change(input, { target: { value: "" } });
  fireEvent.click(screen.getByText("Saved filters"));
  const item = await waitFor(() => screen.getByText("Design roles"));
  fireEvent.click(item);
  expect(input.value).toBe("designer");

  fireEvent.click(screen.getByText("Saved filters"));
  await waitFor(() => screen.getByText("Design roles"));
  fireEvent.click(document.querySelector('[data-icon="trash"]'));
  await waitFor(() => expect(screen.queryByText("Saved filters")).toBeNull());

  expect(listSavedFilters).not.toHaveBeenCalled();
  expect(createSavedFilter).not.toHaveBeenCalled();
  expect(deleteSavedFilter).not.toHaveBeenCalled();
});

// TC-523-J02
it("TC-523-J02 a fresh mount (simulated reload) never carries over a previous instance's preset", async () => {
  const { unmount } = await renderMock();
  const input = screen.getByPlaceholderText("Title, company…");
  fireEvent.change(input, { target: { value: "designer" } });
  const nameInput = await openSaveDialog();
  fireEvent.change(nameInput, { target: { value: "Design roles" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect(await screen.findByText("Saved filters")).toBeInTheDocument();

  unmount();
  await renderMock();
  expect(screen.queryByText("Saved filters")).toBeNull();
});

// TC-523-J03
it("TC-523-J03 the 5-preset ceiling and the no-active-filters gate behave the same with zero network", async () => {
  await renderMock();
  const input = screen.getByPlaceholderText("Title, company…");

  for (let i = 0; i < 5; i++) {
    fireEvent.change(input, { target: { value: "role-" + i } });
    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "Preset " + i } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(screen.queryByPlaceholderText("Filter name…")).toBeNull());
  }
  fireEvent.change(input, { target: { value: "" } });
  expect(screen.queryByText("Save filter")).toBeNull();

  const postedSelect = [...document.querySelectorAll("select")].find((s) =>
    [...s.options].some((o) => o.text === "Any time")
  );
  fireEvent.change(postedSelect, { target: { value: "any" } });
  expect(screen.queryByText("Save filter")).toBeNull();
});
