package com.davidcreate.jobhub.crawler.unit_tests.domain.model;

import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPost domain model — locations")
class JobPostTest {

    @Test
    @DisplayName("QAE-CRAWL-STORE-1: opening set carries primary opening first, plus additional openings")
    void openingSetCarriesPrimaryFirstPlusAdditional() {
        JobPost jobPost = JobPost.builder()
                .title("Backend Engineer")
                .url("https://example.com/job/1")
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder()
                                .country("Netherlands")
                                .city("Amsterdam")
                                .primary(false)
                                .build()))
                .build();

        List<JobPostLocation> locations = jobPost.locations();

        assertThat(locations).hasSize(2);
        assertThat(locations.get(0).isPrimary()).isTrue();
        assertThat(locations.get(0).getCity()).isEqualTo("Barcelona");
        assertThat(locations.get(0).getCountry()).isEqualTo("Spain");
        assertThat(locations.get(1).isPrimary()).isFalse();
        assertThat(locations.get(1).getCity()).isEqualTo("Amsterdam");
        assertThat(locations.get(1).getCountry()).isEqualTo("Netherlands");
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-2 (unit): single-opening post has exactly one primary location")
    void singleOpeningPostHasExactlyOnePrimaryLocation() {
        JobPost jobPost = JobPost.builder()
                .title("Backend Engineer")
                .url("https://example.com/job/2")
                .city("Madrid")
                .country("Spain")
                .build();

        List<JobPostLocation> locations = jobPost.locations();

        assertThat(locations).hasSize(1);
        assertThat(locations.get(0).isPrimary()).isTrue();
        assertThat(locations.get(0).getCity()).isEqualTo("Madrid");
        assertThat(locations.get(0).getCountry()).isEqualTo("Spain");
    }

    @Test
    @DisplayName("A post with no city/country has no locations")
    void noLocationDataYieldsEmptyLocations() {
        JobPost jobPost = JobPost.builder()
                .title("Backend Engineer")
                .url("https://example.com/job/3")
                .build();

        assertThat(jobPost.locations()).isEmpty();
    }
}
