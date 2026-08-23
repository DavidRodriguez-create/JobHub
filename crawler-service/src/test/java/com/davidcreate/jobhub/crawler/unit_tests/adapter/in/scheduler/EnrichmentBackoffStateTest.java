package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentBackoffState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnrichmentBackoffState Unit Tests")
class EnrichmentBackoffStateTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final Logger BACKOFF_LOG = Logger.getLogger(EnrichmentBackoffState.class.getName());

    private EnrichmentBackoffState state;
    private CapturingHandler handler;

    @BeforeEach
    void setUp() {
        state = new EnrichmentBackoffState();
        state.enabled = true;
        state.stepMinutes = 30;
        state.maxMinutes = 120;

        handler = new CapturingHandler();
        BACKOFF_LOG.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        BACKOFF_LOG.removeHandler(handler);
    }

    @Test
    @DisplayName("C6: a fresh holder arms a 30 min backoff after a failed pass")
    void freshHolderArmsBackoffAfterFailure() {
        state.onPassResult(5, 0, false, NOW);

        assertThat(state.isBackedOff(NOW)).isTrue();
        assertThat(state.isBackedOff(NOW.plus(29, ChronoUnit.MINUTES))).isTrue();
        assertThat(state.isBackedOff(NOW.plus(30, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C7: a 2nd consecutive failure extends the delay to 60 min from that failure's instant")
    void secondConsecutiveFailureExtendsDelay() {
        state.onPassResult(5, 0, false, NOW);
        Instant secondFailureInstant = NOW.plus(1, ChronoUnit.MINUTES);

        state.onPassResult(5, 0, false, secondFailureInstant);

        assertThat(state.isBackedOff(secondFailureInstant.plus(59, ChronoUnit.MINUTES))).isTrue();
        assertThat(state.isBackedOff(secondFailureInstant.plus(60, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C8: delay caps at 120 min after the 4th consecutive failure and stays there")
    void delayCapsAt120AndStaysThere() {
        state.onPassResult(3, 0, false, NOW);
        state.onPassResult(3, 0, false, NOW.plus(1, ChronoUnit.MINUTES));
        state.onPassResult(3, 0, false, NOW.plus(2, ChronoUnit.MINUTES));
        Instant fourthFailureInstant = NOW.plus(3, ChronoUnit.MINUTES);

        state.onPassResult(3, 0, false, fourthFailureInstant);

        assertThat(state.isBackedOff(fourthFailureInstant.plus(119, ChronoUnit.MINUTES))).isTrue();
        assertThat(state.isBackedOff(fourthFailureInstant.plus(120, ChronoUnit.MINUTES))).isFalse();

        Instant fifthFailureInstant = fourthFailureInstant.plus(4, ChronoUnit.MINUTES);
        state.onPassResult(3, 0, false, fifthFailureInstant);

        assertThat(state.isBackedOff(fifthFailureInstant.plus(119, ChronoUnit.MINUTES))).isTrue();
        assertThat(state.isBackedOff(fifthFailureInstant.plus(120, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C9: a pass with >=1 success resets the backoff to zero")
    void successResetsBackoff() {
        state.onPassResult(5, 0, false, NOW);
        assertThat(state.isBackedOff(NOW)).isTrue();

        state.onPassResult(5, 2, false, NOW.plus(1, ChronoUnit.MINUTES));

        assertThat(state.isBackedOff(NOW.plus(1, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C10: a pass with no pending rows (attempted=0) is neutral, leaves the backoff unchanged")
    void noPendingRowsIsNeutral() {
        state.onPassResult(5, 0, false, NOW);
        Instant checkInstant = NOW.plus(10, ChronoUnit.MINUTES);
        boolean backedOffBefore = state.isBackedOff(checkInstant);

        state.onPassResult(0, 0, false, checkInstant);

        assertThat(state.isBackedOff(checkInstant)).isEqualTo(backedOffBefore);
        assertThat(state.isBackedOff(checkInstant)).isTrue();
        assertThat(state.isBackedOff(NOW.plus(30, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C11: a cancelled pass is neutral, no extension")
    void cancelledPassIsNeutral() {
        state.onPassResult(5, 0, false, NOW);

        state.onPassResult(3, 0, true, NOW.plus(10, ChronoUnit.MINUTES));

        assertThat(state.isBackedOff(NOW.plus(29, ChronoUnit.MINUTES))).isTrue();
        assertThat(state.isBackedOff(NOW.plus(30, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    @DisplayName("C12: disabled config -> isBackedOff is always false regardless of failures")
    void disabledConfigNeverBacksOff() {
        state.enabled = false;

        state.onPassResult(5, 0, false, NOW);

        assertThat(state.isBackedOff(NOW)).isFalse();
    }

    @Test
    @DisplayName("C19: exactly one INFO line when the backoff is armed/extended, skipped ticks log at DEBUG or below")
    void logsExactlyOneInfoLineWhenArmed() {
        state.onPassResult(5, 0, false, NOW);

        List<LogRecord> infoLines = handler.at(Level.INFO);
        assertThat(infoLines).hasSize(1);
        assertThat(infoLines.get(0).getMessage()).contains("30").contains(NOW.plus(30, ChronoUnit.MINUTES).toString());
        assertThat(handler.records).hasSize(1);
    }

    private static class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> at(Level level) {
            return records.stream().filter(r -> r.getLevel().intValue() == level.intValue()).toList();
        }
    }
}
