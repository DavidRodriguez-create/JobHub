package com.davidcreate.jobhub.crawler.unit_tests.logging;

import com.davidcreate.jobhub.crawler.logging.MaskingLogFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingLogFilterTest {

    private static final String MASK = "**********";

    @Test
    void masksXServiceKeyHeaderValue() {
        assertThat(MaskingLogFilter.mask("headers: X-Service-Key: dev-internal-service-key, Accept: */*"))
                .doesNotContain("dev-internal-service-key")
                .contains("X-Service-Key:")
                .contains(MASK);
        assertThat(MaskingLogFilter.mask("{\"x-service-key\":\"dev-internal-service-key\"}"))
                .doesNotContain("dev-internal-service-key")
                .contains("\"x-service-key\":\"" + MASK);
    }

    @Test
    void masksBearerTokenButKeepsPrefix() {
        assertThat(MaskingLogFilter.mask("forwarding Bearer abc.def.ghi downstream"))
                .isEqualTo("forwarding Bearer " + MASK + " downstream");
    }

    @Test
    void masksEmailAddresses() {
        assertThat(MaskingLogFilter.mask("actor a@b.com performed the action"))
                .doesNotContain("a@b.com")
                .contains(MASK);
    }

    @Test
    void leavesNonSensitiveTextUntouched() {
        String line = "-> GET /internal/trigger-requests (InternalTriggerRequestResource.list)";
        assertThat(MaskingLogFilter.mask(line)).isEqualTo(line);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(MaskingLogFilter.mask(null)).isNull();
        assertThat(MaskingLogFilter.mask("")).isEmpty();
    }
}
