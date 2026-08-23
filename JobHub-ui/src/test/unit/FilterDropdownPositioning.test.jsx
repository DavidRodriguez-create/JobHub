/**
 * Regression tests for story #458 follow-up: the sticky `.search-filters` column
 * (position: sticky; max-height; overflow-y: auto) is a clip container, so the
 * MultiSelect dropdown and SavedFiltersDropdown popover must not rely on
 * `position: absolute` relative to that column (they would be visually clipped, most
 * severely for the lowest filter fields). Both are rendered with `position: fixed`,
 * positioned from their trigger's own bounding rect, so they escape the ancestor's
 * overflow clip while staying real DOM children of the click-outside wrapper.
 *
 * Pure render tests, no JobSearchScreen wrapper, no API mocks.
 */
import React from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

const { MultiSelect, SavedFiltersDropdown } = await import("../../components/FilterComponents.jsx");

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("MultiSelect dropdown escapes the sticky .search-filters clip (position: fixed)", () => {
  it("the open dropdown is positioned fixed, not absolute, so an overflow:auto ancestor cannot clip it", () => {
    render(
      <MultiSelect
        label="All locations"
        options={[{ value: "Spain", label: "Spain", count: 3 }]}
        applied={new Set()}
        onApply={vi.fn()}
        onClearApplied={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText("All locations"));

    const dropdown = screen.getByTestId("multiselect-dropdown");
    expect(dropdown.style.position).toBe("fixed");
    expect(dropdown.style.position).not.toBe("absolute");
  });

  it("the dropdown coordinates come from the trigger's own bounding rect (top/left are set, not left implicit)", () => {
    render(
      <MultiSelect
        label="All companies"
        options={[{ value: "Acme Corp", label: "Acme Corp", count: 5 }]}
        applied={new Set()}
        onApply={vi.fn()}
        onClearApplied={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText("All companies"));

    const dropdown = screen.getByTestId("multiselect-dropdown");
    // jsdom's getBoundingClientRect returns zeros (no real layout engine), but the
    // component must still compute and set explicit top/left (never inherit "auto"/""
    // the way a plain `position: absolute; top: calc(100% + 4px)` rule would).
    expect(dropdown.style.top).not.toBe("");
    expect(dropdown.style.left).not.toBe("");
  });

  it("stays a real DOM descendant of the click-outside wrapper (not portaled), so click-outside keeps working", () => {
    const onApply = vi.fn();
    const { container } = render(
      <MultiSelect
        label="All locations"
        options={[{ value: "Spain", label: "Spain", count: 3 }]}
        applied={new Set()}
        onApply={onApply}
        onClearApplied={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText("All locations"));
    const dropdown = screen.getByTestId("multiselect-dropdown");

    // The dropdown is a DOM child of the same wrapper as the trigger (fixed positioning
    // is CSS-only; it does not move the node in the DOM tree the way a portal would).
    expect(container.contains(dropdown)).toBe(true);

    // Clicking inside the dropdown must not be treated as an outside click.
    fireEvent.mouseDown(dropdown);
    expect(screen.queryByTestId("multiselect-dropdown")).toBeInTheDocument();

    // A real outside click still closes it and discards pending (existing behaviour).
    fireEvent.mouseDown(document.body);
    expect(screen.queryByTestId("multiselect-dropdown")).not.toBeInTheDocument();
  });
});

describe("SavedFiltersDropdown popover escapes the sticky .search-filters clip (position: fixed)", () => {
  it("the open popover is positioned fixed, not absolute, so an overflow:auto ancestor cannot clip it", () => {
    render(
      <SavedFiltersDropdown
        filters={[{ name: "My filter", state: {} }]}
        onApply={vi.fn()}
        onDelete={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText("Saved filters"));

    const popover = screen.getByTestId("saved-filters-dropdown");
    expect(popover.style.position).toBe("fixed");
    expect(popover.style.position).not.toBe("absolute");
  });

  it("stays a real DOM descendant of the click-outside wrapper (not portaled)", () => {
    const { container } = render(
      <SavedFiltersDropdown
        filters={[{ name: "My filter", state: {} }]}
        onApply={vi.fn()}
        onDelete={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText("Saved filters"));
    const popover = screen.getByTestId("saved-filters-dropdown");
    expect(container.contains(popover)).toBe(true);

    fireEvent.mouseDown(document.body);
    expect(screen.queryByTestId("saved-filters-dropdown")).not.toBeInTheDocument();
  });
});
