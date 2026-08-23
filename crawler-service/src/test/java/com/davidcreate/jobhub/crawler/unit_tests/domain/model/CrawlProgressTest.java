package com.davidcreate.jobhub.crawler.unit_tests.domain.model;

import com.davidcreate.jobhub.crawler.domain.model.CrawlProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CrawlProgress domain model")
class CrawlProgressTest {

    @Test
    @DisplayName("TC-513-B8: builder/getter round-trip -- every field returns exactly what was set")
    void builderRoundTrip() {
        CrawlProgress progress = CrawlProgress.builder()
                .targetsVisited(3)
                .newPosts(47)
                .lastCompanyName("Klaviyo")
                .lastSourceType("greenhouse")
                .lastFoundPosts(142)
                .lastNewPosts(16)
                .build();

        assertThat(progress.getTargetsVisited()).isEqualTo(3);
        assertThat(progress.getNewPosts()).isEqualTo(47);
        assertThat(progress.getLastCompanyName()).isEqualTo("Klaviyo");
        assertThat(progress.getLastSourceType()).isEqualTo("greenhouse");
        assertThat(progress.getLastFoundPosts()).isEqualTo(142);
        assertThat(progress.getLastNewPosts()).isEqualTo(16);
    }
}
