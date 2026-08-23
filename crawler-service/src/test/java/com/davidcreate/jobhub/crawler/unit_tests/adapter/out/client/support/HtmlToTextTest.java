package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.HtmlToText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlToText Unit Tests")
class HtmlToTextTest {

    @Test
    @DisplayName("strips real HTML tags to plain text")
    void stripsTags() {
        String out = HtmlToText.clean("<p>Hello <strong>world</strong></p>");
        assertThat(out).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("decodes double-escaped Greenhouse markup and strips it")
    void decodesDoubleEscaped() {
        // Greenhouse style: the HTML itself is entity-escaped, sometimes twice.
        String out = HtmlToText.clean("&amp;lt;p&amp;gt;As Head of Engineering&amp;lt;/p&amp;gt;");
        assertThat(out).isEqualTo("As Head of Engineering");
        assertThat(out).doesNotContain("&lt;", "<", "&amp;");
    }

    @Test
    @DisplayName("keeps block-level line breaks and bullet points")
    void keepsStructure() {
        String out = HtmlToText.clean("<p>Requirements:</p><ul><li>Java</li><li>SQL</li></ul>");
        assertThat(out).isEqualTo("Requirements:\n• Java\n• SQL");
    }

    @Test
    @DisplayName("turns <br> into newlines")
    void brBecomesNewline() {
        assertThat(HtmlToText.clean("Line one<br>Line two")).isEqualTo("Line one\nLine two");
    }

    @Test
    @DisplayName("resolves common entities and collapses excess whitespace")
    void resolvesEntitiesAndWhitespace() {
        String out = HtmlToText.clean("<p>R&amp;D&nbsp;team   with   &quot;scale&quot;</p>");
        assertThat(out).isEqualTo("R&D team with \"scale\"");
    }

    @Test
    @DisplayName("leaves already-plain text essentially unchanged")
    void plainTextUnchanged() {
        assertThat(HtmlToText.clean("Senior Backend Engineer in London")).
                isEqualTo("Senior Backend Engineer in London");
    }

    @Test
    @DisplayName("handles null and blank")
    void handlesNullAndBlank() {
        assertThat(HtmlToText.clean(null)).isNull();
        assertThat(HtmlToText.clean("   ")).isEmpty();
    }
}
