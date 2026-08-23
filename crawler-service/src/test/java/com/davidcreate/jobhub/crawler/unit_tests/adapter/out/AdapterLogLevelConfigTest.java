package com.davidcreate.jobhub.crawler.unit_tests.adapter.out;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-513-B19 (ADR 0029, story #513): the config-as-data half of AC-513-5/AC-513-6. Loads the
 * source resource files directly (not via the test classpath, which shadows
 * {@code application.properties} with the test-only copy) so this asserts what actually ships,
 * not what the test JVM happens to load.
 */
@DisplayName("Adapter log-level config (ADR 0029, story #513)")
class AdapterLogLevelConfigTest {

    private static final String KEY = "quarkus.log.category.\"com.davidcreate.jobhub.crawler.adapter.out\".level";

    @Test
    @DisplayName("TC-513-B19: application.properties carries the exact env-overridable category key")
    void applicationPropertiesCarriesEnvOverridableKey() throws IOException {
        Properties props = loadProperties("src/main/resources/application.properties");

        assertThat(props.getProperty(KEY)).isEqualTo("${CRAWLER_ADAPTER_LOG_LEVEL:INFO}");
    }

    @Test
    @DisplayName("TC-513-B19: application-dev.properties pins it unconditionally to DEBUG (no ${...} placeholder)")
    void applicationDevPropertiesPinsToDebug() throws IOException {
        Properties props = loadProperties("src/main/resources/application-dev.properties");

        assertThat(props.getProperty(KEY)).isEqualTo("DEBUG");
    }

    private static Properties loadProperties(String relativePath) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(relativePath))) {
            props.load(in);
        }
        return props;
    }
}
