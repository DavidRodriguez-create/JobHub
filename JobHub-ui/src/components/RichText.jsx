import React from "react";

// Renders a job description that may be plain text OR HTML (often entity-escaped by the
// crawler, e.g. "&lt;p&gt;…"). We never inject markup (no dangerouslySetInnerHTML) — the
// raw value comes from crawled third-party sites, so that would be an XSS vector. Instead
// we parse it, pull out *text only* via the DOM, and rebuild a clean, safe set of
// paragraphs / headings / bullet lists.

function decodeEntities(s) {
  const ta = document.createElement("textarea");
  ta.innerHTML = s;
  return ta.value;
}

function clean(s) {
  // Collapse whitespace and normalise non-breaking spaces so text reads naturally.
  return String(s || "").replace(/ /g, " ").replace(/\s+/g, " ").trim();
}

// A short paragraph whose entire text is bold reads as a section heading (e.g. Airbnb's
// "The Community You Will Join:").
function isHeadingPara(el) {
  const txt = clean(el.textContent);
  if (!txt || txt.length > 80) return false;
  const bold = clean([...el.querySelectorAll("strong, b")].map((b) => b.textContent).join(" "));
  return Boolean(bold) && bold === txt;
}

// Only these tags are treated as real formatting HTML (story #458). A stray tag-looking
// substring outside this whitelist (e.g. an accidental/malicious "<script>") never routes
// crawled text through DOMParser: it stays inert plain text instead, closing an XSS gap
// where a script-like run inside an otherwise plain description could get real-parsed.
const FORMATTING_TAG_RE = /<\/?(p|div|section|article|ul|ol|li|br|h[1-6]|span|strong|b|em|i|a)\b[^>]*>/i;

// Recognises a bullet-list marker ("•", "-", "*") leading a line, optionally followed by
// text. Matches a bare marker with nothing after it too (e.g. a trailing empty bullet).
const BULLET_LEAD_RE = /^[•*-](?:\s+(.*))?$/;
// Bullet character used as an inline item separator on a single line (e.g. crawled text
// with no real line breaks: "Own delivery • Mentor others"). Only the unambiguous "•"
// character is used for inline splitting: "-" and "*" are too easily confused with normal
// punctuation mid-sentence (guarded by TC-458-B10).
const BULLET_INLINE_SEP_RE = /\s*•\s*/;

// Given one logical line of text, decide whether it is a bullet-list item line and return
// the item text(s) it represents. Returns null when the line is an ordinary sentence.
function splitBulletLine(line) {
  const trimmed = line.trim();
  if (!trimmed) return null;
  const leadMatch = BULLET_LEAD_RE.exec(trimmed);
  const hasLead = Boolean(leadMatch);
  const rest = hasLead ? (leadMatch[1] || "") : trimmed;
  const inlineParts = rest.split(BULLET_INLINE_SEP_RE).map((s) => s.trim()).filter(Boolean);
  const hasInlineSplit = inlineParts.length > 1;
  if (!hasLead && !hasInlineSplit) return null;
  const items = hasInlineSplit ? inlineParts : (rest.trim() ? [rest.trim()] : []);
  return { items };
}

// Splits a run of lines into ordered p/ul blocks. Consecutive bullet-marker lines merge
// into one ul; a bulleted "group" that resolves to a single item is treated as a false
// positive (a stray marker used for other reasons) and falls back to a plain paragraph
// (TC-458-B14, B15) instead of a one-item list.
function blocksFromLines(lines) {
  const blocks = [];
  let group = null;
  function flushGroup() {
    if (!group) return;
    if (group.items.length >= 2) {
      blocks.push({ type: "ul", items: group.items });
    } else if (group.items.length === 1) {
      const text = clean(group.rawLines.join(" "));
      if (text) blocks.push({ type: "p", text });
    }
    group = null;
  }
  lines.forEach((line) => {
    if (!line.trim()) { flushGroup(); return; }
    const parsed = splitBulletLine(line);
    if (parsed) {
      if (!group) group = { items: [], rawLines: [] };
      group.items.push(...parsed.items);
      group.rawLines.push(line.trim());
    } else {
      flushGroup();
      const t = clean(line);
      if (t) blocks.push({ type: "p", text: t });
    }
  });
  flushGroup();
  return blocks;
}

// Entry point for any plain-text run (top-level plain text, or the inner text of an HTML
// leaf element with no nested block children): blank-line-separated chunks each become
// their own paragraph/bullet-group, and order is preserved throughout.
function parseTextRun(text) {
  return String(text || "")
    .split(/\n{2,}/)
    .flatMap((chunk) => blocksFromLines(chunk.split("\n")))
    .filter((b) => (b.type === "ul" ? b.items.length > 0 : Boolean(b.text)));
}

// Rebuilds the text of an element, turning real <br> children into line breaks so bullet
// lines separated by <br> (instead of "\n") are still recognised (TC-458-B07).
function rawTextWithBreaks(el) {
  let out = "";
  el.childNodes.forEach((n) => {
    if (n.nodeType === Node.TEXT_NODE) {
      out += n.textContent;
    } else if (n.nodeType === Node.ELEMENT_NODE) {
      out += n.tagName.toLowerCase() === "br" ? "\n" : n.textContent;
    }
  });
  return out;
}

function blocksFrom(node) {
  const blocks = [];
  node.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      const t = clean(child.textContent);
      if (t) blocks.push({ type: "p", text: t });
      return;
    }
    if (child.nodeType !== Node.ELEMENT_NODE) return;
    const tag = child.tagName.toLowerCase();
    if (/^h[1-6]$/.test(tag)) {
      const t = clean(child.textContent);
      if (t) blocks.push({ type: "h", text: t });
    } else if (tag === "ul" || tag === "ol") {
      const items = [...child.querySelectorAll(":scope > li")].map((li) => clean(li.textContent)).filter(Boolean);
      if (items.length) blocks.push({ type: "ul", items });
    } else if (tag === "br" || tag === "script" || tag === "style") {
      // skip
    } else if (["p", "div", "section", "article", "li"].includes(tag)) {
      if (isHeadingPara(child)) {
        blocks.push({ type: "h", text: clean(child.textContent) });
      } else if (child.querySelector("ul, ol, p, div, section, article")) {
        blocks.push(...blocksFrom(child)); // preserve order of nested blocks
      } else {
        // Leaf text run: may itself be a plain-text bullet list (story #458), e.g. a
        // crawler-supplied <p>/<div> whose "items" are only separated by "•"/"-"/"*"
        // markers instead of real <ul>/<li> markup.
        blocks.push(...parseTextRun(rawTextWithBreaks(child)));
      }
    } else {
      // inline element (span, strong, a, em, …): treat its text as paragraph content
      const t = clean(child.textContent);
      if (t) blocks.push({ type: "p", text: t });
    }
  });
  return blocks;
}

function toBlocks(raw) {
  const value = String(raw || "").trim();
  if (!value) return [];
  // The crawler stores HTML entity-escaped ("&lt;p&gt;"); decode that first. If the value
  // is already real HTML or plain text, decoding is a harmless no-op for our purposes.
  const escaped = /&lt;\/?[a-z]/i.test(value);
  const html = escaped ? decodeEntities(value) : value;
  if (!FORMATTING_TAG_RE.test(html)) {
    // No real formatting tags → plain text. Split into paragraphs/bullet-lists (story #458).
    return parseTextRun(html);
  }
  const body = new DOMParser().parseFromString(html, "text/html").body;
  const blocks = blocksFrom(body);
  return blocks.length ? blocks : [{ type: "p", text: clean(body.textContent) }].filter((b) => b.text);
}

export default function RichText({ text, style }) {
  const blocks = React.useMemo(() => toBlocks(text), [text]);
  if (!blocks.length) {
    return <p style={{ fontSize: 13, color: "var(--color-ink-3)", ...style }}>No description provided.</p>;
  }
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 680, ...style }}>
      {blocks.map((b, i) => {
        if (b.type === "h") {
          return (
            <div key={i} style={{
              fontSize: 13, fontWeight: 700, color: "var(--color-ink)",
              marginTop: i ? 8 : 0, letterSpacing: "-0.006em",
              borderBottom: "1px solid var(--color-border)", paddingBottom: 4,
            }}>
              {b.text}
            </div>
          );
        }
        if (b.type === "ul") {
          return (
            <ul key={i} style={{ margin: 0, paddingLeft: 20, display: "flex", flexDirection: "column", gap: 5 }}>
              {b.items.map((it, j) => (
                <li key={j} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.6 }}>{it}</li>
              ))}
            </ul>
          );
        }
        return (
          <p key={i} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.7, margin: 0 }}>{b.text}</p>
        );
      })}
    </div>
  );
}
