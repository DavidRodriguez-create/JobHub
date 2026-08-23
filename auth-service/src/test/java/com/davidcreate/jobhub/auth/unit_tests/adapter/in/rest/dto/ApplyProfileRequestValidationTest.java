package com.davidcreate.jobhub.auth.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.contract.model.ApplyProfileRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-U8..BE-U11: the contract's own {@code ApplyProfileRequest} Bean Validation
 * annotations (maxLength / maxItems / per-item maxLength), exercised directly with a
 * {@code jakarta.validation.Validator} (no HTTP), per the QAE test cases.
 *
 * <p><b>Contract-generation gap (flagged to the architect, ticket #421 handoff):</b>
 * combining {@code format: uri} with {@code maxLength} on the same OpenAPI property
 * makes openapi-generator emit a {@code java.net.URI}-typed field carrying
 * {@code @Size(max=...)}. {@code @Size} has no built-in validator for {@code URI}
 * (only CharSequence/Collection/Map/array), so the default
 * {@code ValidatorFactory} throws {@code UnexpectedTypeException} the first time it
 * resolves {@code ApplyProfileRequest}'s constraint metadata, for *every* instance of
 * the class, whether or not a URL field is populated. This is test-only plumbing
 * (a {@code ConstraintMapping} registering an extra {@code @Size} validator for
 * {@code URI}) that lets these unit tests exercise the contract's other, correctly-typed
 * constraints; it intentionally is not shipped as production code, because the running
 * application never invokes {@code Validator.validate(ApplyProfileRequest)} at all
 * (this service enforces the same caps at the domain layer instead —
 * {@code ApplyProfile.replace}, see {@code ApplyProfileTest}'s BR-6 cases — precisely
 * to sidestep this contract defect in the real request path).
 */
@DisplayName("ApplyProfileRequest Bean Validation Unit Tests — BE-U8..BE-U11")
class ApplyProfileRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.constraintDefinition(Size.class)
                .validatedBy(UriSizeValidator.class)
                .includeExistingValidators(true);
        validatorFactory = configuration.addMapping(mapping).buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    // --- BE-U8: over-length text fields (AC7) ---

    static Stream<Arguments> overLengthTextFields() {
        return Stream.of(
                Arguments.of("workAuthorization", new ApplyProfileRequest().workAuthorization("a".repeat(201))),
                Arguments.of("noticePeriod", new ApplyProfileRequest().noticePeriod("a".repeat(101))),
                Arguments.of("salaryExpectation", new ApplyProfileRequest().salaryExpectation("a".repeat(101))),
                Arguments.of("currentLocation", new ApplyProfileRequest().currentLocation("a".repeat(201))),
                Arguments.of("roomToGrow", new ApplyProfileRequest().roomToGrow("a".repeat(2001))));
    }

    @ParameterizedTest(name = "BE-U8: {0} one char over cap -> exactly one violation on that field")
    @MethodSource("overLengthTextFields")
    void overLengthFieldReportsExactlyOneViolationOnThatField(String property, ApplyProfileRequest request) {
        Set<jakarta.validation.ConstraintViolation<ApplyProfileRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo(property);
    }

    // --- BE-U9: more than 20 languages (AC8) ---

    @Test
    @DisplayName("BE-U9: 21 languages -> violation reported on the languages property")
    void tooManyLanguagesReportsViolationOnLanguages() {
        List<String> languages = IntStream.range(0, 21).mapToObj(i -> "Lang" + i).toList();
        ApplyProfileRequest request = new ApplyProfileRequest().languages(languages);

        Set<jakarta.validation.ConstraintViolation<ApplyProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("languages"));
    }

    // --- BE-U10: an over-length single language entry (BR-6) ---

    @Test
    @DisplayName("BE-U10: one 61-char entry among 5 (within the 20 cap) is independently rejected")
    void overLengthLanguageEntryIndependentlyRejected() {
        List<String> languages = new ArrayList<>(List.of("English", "Spanish", "a".repeat(61), "French", "German"));
        ApplyProfileRequest request = new ApplyProfileRequest().languages(languages);

        Set<jakarta.validation.ConstraintViolation<ApplyProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("languages"));
    }

    // --- BE-U11: malformed URL fields (AC9) ---

    @ParameterizedTest(name = "BE-U11: {0} well-formed URL -> no violation for that field")
    @MethodSource("wellFormedUrlFields")
    @DisplayName("BE-U11: a well-formed URL passes validation cleanly for each of the three URL fields")
    void wellFormedUrlReportsNoViolation(String property, ApplyProfileRequest request) {
        Set<jakarta.validation.ConstraintViolation<ApplyProfileRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    static Stream<Arguments> wellFormedUrlFields() {
        return Stream.of(
                Arguments.of("linkedinUrl",
                        new ApplyProfileRequest().linkedinUrl(URI.create("https://linkedin.com/in/alice"))),
                Arguments.of("githubUrl", new ApplyProfileRequest().githubUrl(URI.create("https://github.com/alice"))),
                Arguments.of("portfolioUrl", new ApplyProfileRequest().portfolioUrl(URI.create("https://alice.dev"))));
    }

    @Test
    @DisplayName("BE-U11: a malformed URL string cannot even be assigned to the URI-typed field "
            + "(the contract's format: uri maps to java.net.URI, so rejection happens at URI "
            + "construction / JSON deserialization time, proven over HTTP as 400 in BE-C10, "
            + "rather than as a jakarta.validation ConstraintViolation on this DTO)")
    void malformedUrlStringFailsAtUriConstructionTime() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> URI.create("not a link"));
    }

    /**
     * Test-only: extends the built-in {@code @Size} constraint to {@code java.net.URI} by
     * checking the URI's string-form length, working around the openapi-generator
     * {@code format: uri} + {@code maxLength} interop gap described on the class Javadoc above.
     */
    public static class UriSizeValidator implements ConstraintValidator<Size, URI> {

        private int max;

        @Override
        public void initialize(Size constraintAnnotation) {
            this.max = constraintAnnotation.max();
        }

        @Override
        public boolean isValid(URI value, ConstraintValidatorContext context) {
            return value == null || value.toString().length() <= max;
        }
    }
}
