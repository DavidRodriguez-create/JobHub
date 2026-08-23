package com.davidcreate.jobhub.application.unit_tests.adapter.out.persistence.support;

import com.davidcreate.jobhub.application.adapter.out.persistence.support.KeywordExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeywordExtractor")
class KeywordExtractorTest {

    @Test
    @DisplayName("extracts and counts lowercase keywords across titles, excluding stop words")
    void extractsTopKeywords() {
        List<String> titles = List.of(
                "Senior Backend Java Developer",
                "Backend Java Engineer",
                "Java Developer",
                "Frontend Developer");

        List<String> top = KeywordExtractor.topKeywords(titles, 5);

        assertThat(top).contains("java", "developer", "backend");
        assertThat(top).doesNotContain("and", "or", "the");
        assertThat(top).hasSizeLessThanOrEqualTo(5);
        // "developer" and "java" each appear 3x, "backend" 2x -- all should rank above
        // the 1x-frequency words ("senior", "engineer", "frontend").
        assertThat(top.indexOf("developer")).isLessThan(top.indexOf("senior"));
        assertThat(top.indexOf("java")).isLessThan(top.indexOf("senior"));
        assertThat(top.indexOf("backend")).isLessThan(top.indexOf("senior"));
    }

    @Test
    @DisplayName("excludes common stop words entirely")
    void excludesStopWords() {
        List<String> titles = List.of("Developer and Engineer for the Team", "Developer or Analyst in the Office");

        List<String> top = KeywordExtractor.topKeywords(titles, 10);

        assertThat(top).doesNotContain("and", "or", "for", "the", "in");
    }

    @Test
    @DisplayName("returns an empty list for no titles")
    void returnsEmptyForNoTitles() {
        assertThat(KeywordExtractor.topKeywords(List.of(), 5)).isEmpty();
    }

    @Test
    @DisplayName("caps the result at the requested size")
    void capsResultSize() {
        List<String> titles = List.of("Alpha Beta Gamma Delta Epsilon Zeta Eta Theta");

        List<String> top = KeywordExtractor.topKeywords(titles, 3);

        assertThat(top).hasSize(3);
    }

    @Test
    @DisplayName("lowercases and strips punctuation from tokens")
    void lowercasesAndStripsPunctuation() {
        List<String> titles = List.of("Senior Java/Kotlin Developer (Remote)", "Java Developer!");

        List<String> top = KeywordExtractor.topKeywords(titles, 10);

        assertThat(top).contains("java", "developer");
        assertThat(top).noneMatch(w -> w.contains("/") || w.contains("(") || w.contains(")") || w.contains("!"));
    }
}
