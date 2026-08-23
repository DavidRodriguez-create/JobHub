package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #408 (ADR 0021), QAE-408-F-01: {@code parseCity}/{@code parseCountry} keep their
 * existing signatures and now return canonicalized values by delegating through {@code
 * LocationNormalizer} (AC-408-49).
 */
@DisplayName("LocationParser facade Unit Tests")
class LocationParserTest {

    @ParameterizedTest
    @DisplayName("QAE-408-F-01: parseCity/parseCountry now canonicalize (AC-408-49)")
    @CsvSource({
            "'Barcelona, Spain',Barcelona,Spain",
            "us,,United States",
            "Ca,California,United States",
            "Remote,,Remote",
            "Amsterdam,Amsterdam,"
    })
    void f01_parseCityAndCountryCanonicalize(String input, String expectedCity, String expectedCountry) {
        assertThat(LocationParser.parseCity(input)).isEqualTo(expectedCity);
        assertThat(LocationParser.parseCountry(input)).isEqualTo(expectedCountry);
    }
}
