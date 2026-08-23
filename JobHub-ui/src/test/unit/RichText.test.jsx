/**
 * Unit tests for RichText component
 * Cases: RT-01..07
 *
 * RT-01: plain text with blank-line separation → multiple <p> elements
 * RT-02: entity-escaped HTML decoded to structured blocks (p/ul/headings)
 * RT-03: <h2> heading element rendered as heading block
 * RT-04: all-bold paragraph treated as heading
 * RT-05: empty string → "No description provided."
 * RT-06: null → "No description provided."
 * RT-07: nbsp and excess whitespace collapsed
 */
import React from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import RichText from "../../components/RichText.jsx";

describe("RichText — plain text and HTML parsing (RT-01..07)", () => {
  // RT-01: plain text with blank lines → paragraphs
  it("RT-01: plain text split on blank lines produces separate paragraphs", () => {
    const text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
    const { container } = render(<RichText text={text} />);
    const paras = container.querySelectorAll("p");
    expect(paras.length).toBeGreaterThanOrEqual(3);
    expect(paras[0].textContent).toBe("First paragraph.");
    expect(paras[1].textContent).toBe("Second paragraph.");
    expect(paras[2].textContent).toBe("Third paragraph.");
  });

  // RT-02: entity-escaped HTML decoded to structured blocks
  it("RT-02: entity-escaped HTML is decoded and rendered as structured blocks", () => {
    const text =
      "&lt;p&gt;Intro paragraph.&lt;/p&gt;" +
      "&lt;ul&gt;&lt;li&gt;Item one&lt;/li&gt;&lt;li&gt;Item two&lt;/li&gt;&lt;/ul&gt;";
    const { container } = render(<RichText text={text} />);
    // Paragraph rendered
    expect(screen.getByText("Intro paragraph.")).toBeInTheDocument();
    // List items rendered
    const listItems = container.querySelectorAll("li");
    expect(listItems.length).toBeGreaterThanOrEqual(2);
    const liTexts = [...listItems].map((li) => li.textContent);
    expect(liTexts).toContain("Item one");
    expect(liTexts).toContain("Item two");
  });

  // RT-03: <h2> heading produces a heading block
  it("RT-03: <h2> element in HTML is rendered as a heading block", () => {
    const text = "&lt;h2&gt;Section Title&lt;/h2&gt;&lt;p&gt;Some content.&lt;/p&gt;";
    const { container } = render(<RichText text={text} />);
    // The heading text should appear with heading styling (the component renders it as div with fontWeight 600)
    expect(screen.getByText("Section Title")).toBeInTheDocument();
    expect(screen.getByText("Some content.")).toBeInTheDocument();
  });

  // RT-04: all-bold paragraph (<strong> wrapping entire text) treated as heading
  it("RT-04: short all-bold paragraph is treated as a section heading", () => {
    const text = "&lt;p&gt;&lt;strong&gt;Requirements:&lt;/strong&gt;&lt;/p&gt;&lt;p&gt;Normal paragraph.&lt;/p&gt;";
    const { container } = render(<RichText text={text} />);
    expect(screen.getByText("Requirements:")).toBeInTheDocument();
    expect(screen.getByText("Normal paragraph.")).toBeInTheDocument();
  });

  // RT-05: empty string → fallback message
  it("RT-05: empty string renders 'No description provided.'", () => {
    render(<RichText text="" />);
    expect(screen.getByText("No description provided.")).toBeInTheDocument();
  });

  // RT-06: null → fallback message
  it("RT-06: null renders 'No description provided.'", () => {
    render(<RichText text={null} />);
    expect(screen.getByText("No description provided.")).toBeInTheDocument();
  });

  // RT-07: non-breaking spaces and excess whitespace are collapsed to readable text
  it("RT-07: nbsp and excess whitespace are collapsed to clean readable text", () => {
    //   is nbsp, multiple spaces and newlines within a paragraph
    const text = "Hello world.  Some  extra   spaces.\n\nSecond   para.";
    const { container } = render(<RichText text={text} />);
    const paras = container.querySelectorAll("p");
    // Each paragraph should have collapsed whitespace
    expect(paras[0].textContent).toBe("Hello world. Some extra spaces.");
    expect(paras[1].textContent).toBe("Second para.");
  });
});

/**
 * Bullet-list detection (story #458, issue #475)
 * Cases: TC-458-B01..B08, B10, B13..B18
 *
 * Plain-text or <p>/<div> text runs that use "bullet", "-", or "*" as line-leading markers
 * should split into a <ul> block instead of collapsing into one run-on <p>. Single stray
 * markers (only one occurrence) must NOT trigger list detection (false-positive guard).
 */
describe("RichText, bullet-list detection (TC-458-B01..B08, B10, B13..B18)", () => {
  const BULLET = "•"; // "•"

  it("TC-458-B01: single line, bullet-delimited plain text -> one ul block", () => {
    const text = `Own end-to-end delivery ${BULLET} Mentor junior engineers ${BULLET} Ship weekly features`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
    const runOn = [...container.querySelectorAll("p")].some((p) => p.textContent.includes("Mentor junior engineers"));
    expect(runOn).toBe(false);
  });

  it("TC-458-B02: newline-separated lines each led by a bullet marker", () => {
    const text = `${BULLET} Own end-to-end delivery\n${BULLET} Mentor junior engineers\n${BULLET} Ship weekly features`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
  });

  it("TC-458-B03: newline-separated lines each led by a hyphen marker", () => {
    const text = "- Own end-to-end delivery\n- Mentor junior engineers\n- Ship weekly features";
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
  });

  it("TC-458-B04: newline-separated lines each led by an asterisk marker", () => {
    const text = "* Own end-to-end delivery\n* Mentor junior engineers\n* Ship weekly features";
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
  });

  it("TC-458-B05: intro sentence followed by bullets, order preserved", () => {
    const text = `What you'll do:\n${BULLET} Own end-to-end delivery\n${BULLET} Mentor junior engineers\n${BULLET} Ship weekly features`;
    const { container } = render(<RichText text={text} />);
    const blocks = [...container.querySelectorAll("p, ul")];
    expect(blocks.length).toBe(2);
    expect(blocks[0].tagName).toBe("P");
    expect(blocks[0].textContent).toBe("What you'll do:");
    expect(blocks[0].textContent).not.toContain(BULLET);
    expect(blocks[1].tagName).toBe("UL");
    const items = [...blocks[1].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
  });

  it("TC-458-B06: HTML <p> whose entire text is bullet-delimited on one line -> ul, not one <p>", () => {
    const text = `&lt;p&gt;Own end-to-end delivery ${BULLET} Mentor junior engineers ${BULLET} Ship weekly features&lt;/p&gt;`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
    const runOn = [...container.querySelectorAll("p")].some((p) => p.textContent.includes("Mentor junior engineers"));
    expect(runOn).toBe(false);
  });

  it("TC-458-B07: HTML <div> with <br>-separated bullets -> ul", () => {
    const text = `&lt;div&gt;${BULLET} Own end-to-end delivery&lt;br&gt;${BULLET} Mentor junior engineers&lt;br&gt;${BULLET} Ship weekly features&lt;/div&gt;`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual([
      "Own end-to-end delivery",
      "Mentor junior engineers",
      "Ship weekly features",
    ]);
  });

  it("TC-458-B08: nested HTML, intro <p> + bullet <p> inside a wrapping <div>, order preserved", () => {
    const text = `&lt;div&gt;&lt;p&gt;Responsibilities:&lt;/p&gt;&lt;p&gt;${BULLET} Own delivery ${BULLET} Mentor others&lt;/p&gt;&lt;/div&gt;`;
    const { container } = render(<RichText text={text} />);
    const blocks = [...container.querySelectorAll("p, ul")];
    expect(blocks.length).toBe(2);
    expect(blocks[0].tagName).toBe("P");
    expect(blocks[0].textContent).toBe("Responsibilities:");
    expect(blocks[1].tagName).toBe("UL");
    const items = [...blocks[1].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual(["Own delivery", "Mentor others"]);
  });

  it("TC-458-B10: mid-word hyphen used as punctuation stays a single paragraph", () => {
    const text = "We offer a 3-5 year growth path with mentorship and clear promotion criteria.";
    const { container } = render(<RichText text={text} />);
    const paras = container.querySelectorAll("p");
    expect(paras.length).toBe(1);
    expect(paras[0].textContent).toBe(text);
    expect(container.querySelectorAll("ul").length).toBe(0);
    expect(container.querySelectorAll("li").length).toBe(0);
  });

  it("TC-458-B13: whitespace-only string -> fallback", () => {
    render(<RichText text={"   \n\t  "} />);
    expect(screen.getByText("No description provided.")).toBeInTheDocument();
  });

  it("TC-458-B14: single stray bullet marker does not falsely become a list", () => {
    const text = `${BULLET} Remote-first team`;
    const { container } = render(<RichText text={text} />);
    expect(container.querySelectorAll("p").length).toBe(1);
    expect(container.querySelectorAll("li").length).toBe(0);
  });

  it("TC-458-B15: single stray hyphen leading marker does not falsely become a list", () => {
    const text = "- Note: this field is optional";
    const { container } = render(<RichText text={text} />);
    expect(container.querySelectorAll("p").length).toBe(1);
    expect(container.querySelectorAll("li").length).toBe(0);
  });

  it("TC-458-B16: trailing empty bullet is dropped, not rendered as an empty li", () => {
    const text = `${BULLET} Own end-to-end delivery\n${BULLET} Mentor junior engineers\n${BULLET}\n`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual(["Own end-to-end delivery", "Mentor junior engineers"]);
  });

  it("TC-458-B17: XSS-safety, a bullet item containing markup-looking text stays inert", () => {
    const text = `${BULLET} Safe item\n${BULLET} <script>alert(1)</script>`;
    const { container } = render(<RichText text={text} />);
    const items = container.querySelectorAll("li");
    expect(items.length).toBe(2);
    expect(items[1].textContent).toContain("<script>alert(1)</script>");
    expect(container.querySelectorAll("script").length).toBe(0);
  });

  it("TC-458-B18: mixed marker characters within the same run are all recognised", () => {
    const text = `${BULLET} Own end-to-end delivery\n- Mentor junior engineers`;
    const { container } = render(<RichText text={text} />);
    const uls = container.querySelectorAll("ul");
    expect(uls.length).toBe(1);
    const items = [...uls[0].querySelectorAll("li")].map((li) => li.textContent);
    expect(items).toEqual(["Own end-to-end delivery", "Mentor junior engineers"]);
  });
});
