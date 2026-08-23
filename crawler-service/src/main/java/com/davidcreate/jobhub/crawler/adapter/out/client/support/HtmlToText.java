package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Converts a job board's HTML description into readable plain text for storage and
 * enrichment. Job APIs return wildly different forms — Greenhouse double-escapes its
 * HTML ({@code &amp;lt;p&amp;gt;}), Workday/Amazon return real HTML — so we first
 * unescape entities until stable (handling single or double encoding), then strip
 * tags while keeping block-level line breaks so the text stays readable.
 */
public final class HtmlToText {

    private HtmlToText() {}

    private static final String BLOCK_TAGS = "br, p, div, li, ul, ol, tr, table, "
            + "h1, h2, h3, h4, h5, h6, section, article, header, footer";

    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            return "";
        }

        // Source content may be double-escaped: unescape until it stops changing so the
        // markup becomes real tags before we strip it (capped to avoid pathological loops).
        String html = raw;
        for (int i = 0; i < 4; i++) {
            String next = StringEscapeUtils.unescapeHtml4(html);
            if (next.equals(html)) {
                break;
            }
            html = next;
        }

        Document doc = Jsoup.parse(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        // Append a literal "\n" marker that survives text() (which collapses real
        // whitespace), then turn the markers into newlines afterwards.
        doc.select(BLOCK_TAGS).append("\\n");
        doc.select("li").prepend("• ");

        String text = doc.text().replace("\\n", "\n");
        return text.replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
