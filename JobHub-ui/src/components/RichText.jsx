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
        const t = clean(child.textContent);
        if (t) blocks.push({ type: "p", text: t });
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
  if (!/<\/?[a-z][\s\S]*?>/i.test(html)) {
    // No tags → plain text. Split on blank lines into paragraphs.
    return html.split(/\n{2,}/).map((p) => ({ type: "p", text: clean(p) })).filter((b) => b.text);
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
    <div style={{ display: "flex", flexDirection: "column", gap: 10, ...style }}>
      {blocks.map((b, i) => {
        if (b.type === "h") {
          return (
            <div key={i} style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink)", marginTop: i ? 4 : 0 }}>
              {b.text}
            </div>
          );
        }
        if (b.type === "ul") {
          return (
            <ul key={i} style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 6 }}>
              {b.items.map((it, j) => (
                <li key={j} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.5 }}>{it}</li>
              ))}
            </ul>
          );
        }
        return (
          <p key={i} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.6, margin: 0 }}>{b.text}</p>
        );
      })}
    </div>
  );
}
