package com.davidcreate.jobhub.crawler.unit_tests.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #398 (ADR 0032, D1, 4th pass): {@code ShutdownFlag} is a plain static holder with no
 * CDI/Quarkus dependency whatsoever -- deliberately so, since the diagnosed regression was
 * that a guard reading a value through an injected bean's client proxy throws
 * {@code IllegalStateException("ArC container not initialized")} once the CDI container is
 * torn down, exactly when that guard is most needed. This test class carries no
 * {@code @QuarkusTest}, no mock of anything CDI-related, and no bean of any kind: it exercises
 * {@link ShutdownFlag#raise()}/{@link ShutdownFlag#isRaised()} as plain static calls, proving
 * they work even where "no CDI container is available" is a literal fact, not a simulation.
 */
@DisplayName("ShutdownFlag Unit Tests")
class ShutdownFlagTest {

    @AfterEach
    void reset() throws Exception {
        Field field = ShutdownFlag.class.getDeclaredField("shuttingDown");
        field.setAccessible(true);
        field.set(null, false);
    }

    @Test
    @DisplayName("isRaised() is false until raise() is called, with no CDI container involved at all")
    void isRaisedReflectsRaiseWithNoCdiInvolved() {
        assertThat(ShutdownFlag.isRaised()).isFalse();

        ShutdownFlag.raise();

        assertThat(ShutdownFlag.isRaised()).isTrue();
    }

    @Test
    @DisplayName("the flag is readable from a thread other than the one that raised it, with no bean lookup")
    void isReadableFromAnotherThreadWithNoBeanLookup() throws Exception {
        CountDownLatch raised = new CountDownLatch(1);
        AtomicBoolean seenByOtherThread = new AtomicBoolean(false);

        Thread reader = new Thread(() -> {
            try {
                assertThat(raised.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            seenByOtherThread.set(ShutdownFlag.isRaised());
        }, "shutdown-flag-reader");
        reader.start();

        ShutdownFlag.raise();
        raised.countDown();
        reader.join(2000);

        assertThat(seenByOtherThread.get()).isTrue();
    }
}
