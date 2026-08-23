package com.davidcreate.jobhub.auth.unit_tests.logging;

import com.davidcreate.jobhub.auth.logging.MaskingLogFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingLogFilterTest {

    private static final String MASK = "**********";

    @Test
    void masksEmailAddresses() {
        assertThat(MaskingLogFilter.mask("login ok for john.doe@gmail.com"))
                .isEqualTo("login ok for " + MASK);
    }

    @Test
    void masksJwtTokens() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJqb2huIn0.aGVsbG8td29ybGQtc2lnbmF0dXJl";
        assertThat(MaskingLogFilter.mask("Authorization header token=" + jwt))
                .doesNotContain(jwt)
                .contains(MASK);
    }

    @Test
    void masksBearerTokenButKeepsPrefix() {
        assertThat(MaskingLogFilter.mask("forwarding Bearer abc.def.ghi downstream"))
                .isEqualTo("forwarding Bearer " + MASK + " downstream");
    }

    @Test
    void masksWholeValueAfterAuthorizationKey() {
        assertThat(MaskingLogFilter.mask("Authorization: Bearer abc.def.ghi"))
                .doesNotContain("abc.def.ghi")
                .startsWith("Authorization:")
                .contains(MASK);
    }

    @Test
    void masksBcryptHash() {
        String hash = "$2b$12$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
        assertThat(MaskingLogFilter.mask("passwordHash=" + hash))
                .doesNotContain(hash)
                .contains(MASK);
    }

    @Test
    void masksKeyedSecretsKeepingTheKey() {
        assertThat(MaskingLogFilter.mask("[verification-code] code=482913"))
                .isEqualTo("[verification-code] code=" + MASK);
    }

    @Test
    void masksPlaintextPasswordInJsonBody() {
        assertThat(MaskingLogFilter.mask("{\"email\":\"a@b.com\",\"password\":\"hunter2\"}"))
                .doesNotContain("hunter2")
                .contains("\"password\":\"" + MASK);
    }

    @Test
    void masksHashedPasswordValueAndField() {
        String hash = "$2b$12$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
        assertThat(MaskingLogFilter.mask("{\"password_hash\":\"" + hash + "\"}"))
                .doesNotContain(hash)
                .contains(MASK);
        // the raw hash is also caught anywhere, even without a key
        assertThat(MaskingLogFilter.mask("stored " + hash)).isEqualTo("stored " + MASK);
    }

    @Test
    void masksFirstAndLastNameWhenKeyed() {
        assertThat(MaskingLogFilter.mask("{\"firstName\":\"John\",\"lastName\":\"Doe\"}"))
                .doesNotContain("John")
                .doesNotContain("Doe")
                .contains("\"firstName\":\"" + MASK)
                .contains("\"lastName\":\"" + MASK);
        assertThat(MaskingLogFilter.mask("registered first_name=Jane last_name=Smith"))
                .isEqualTo("registered first_name=" + MASK + " last_name=" + MASK);
    }

    @Test
    void masksApplyProfileAnswerBankFieldsWhenKeyed() {
        String body = "{\"salaryExpectation\":\"120000\",\"currentLocation\":\"Berlin\","
                + "\"linkedinUrl\":\"https://linkedin.com/in/jdoe\",\"workAuthorization\":\"citizen\","
                + "\"noticePeriod\":\"30days\",\"roomToGrow\":\"leadership\"}";
        assertThat(MaskingLogFilter.mask(body))
                .doesNotContain("120000")
                .doesNotContain("Berlin")
                .doesNotContain("linkedin.com/in/jdoe")
                .doesNotContain("citizen")
                .contains("\"salaryExpectation\":\"" + MASK)
                .contains("\"currentLocation\":\"" + MASK)
                .contains("\"linkedinUrl\":\"" + MASK)
                .contains("\"workAuthorization\":\"" + MASK);
        assertThat(MaskingLogFilter.mask("saved salary_expectation=90000 current_location=Madrid"))
                .isEqualTo("saved salary_expectation=" + MASK + " current_location=" + MASK);
    }

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
    void leavesNonSensitiveTextUntouched() {
        String line = "INSERT auth.user id=8f3c1d2e-0000-0000-0000-000000000001 emailVerified=false";
        assertThat(MaskingLogFilter.mask(line)).isEqualTo(line);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(MaskingLogFilter.mask(null)).isNull();
        assertThat(MaskingLogFilter.mask("")).isEmpty();
    }
}
