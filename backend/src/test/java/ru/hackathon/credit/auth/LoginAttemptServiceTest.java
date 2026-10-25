package ru.hackathon.credit.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** Часы, которые двигаются вручную. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-24T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterLimitAndReportsWaitTime() {
        for (int i = 0; i < 2; i++) {
            service.recordFailure("Romashka", "10.0.0.1");
        }
        assertEquals(Duration.ZERO, service.blockedFor("romashka", "10.0.0.1"));
        service.recordFailure("romashka", "10.0.0.1");
        clock.now = clock.now.plusSeconds(60);
        assertEquals(Duration.ofMinutes(14), service.blockedFor("ROMASHKA", "10.0.0.1"));
    }

    @Test
    void otherAddressIsNotBlocked() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("romashka", "10.0.0.1");
        }
        assertEquals(Duration.ZERO, service.blockedFor("romashka", "10.0.0.2"));
        assertTrue(service.blockedFor("romashka", "10.0.0.1").toMinutes() > 0);
    }

    @Test
    void oldFailuresExpireAfterWindow() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("romashka", "10.0.0.1");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertEquals(Duration.ZERO, service.blockedFor("romashka", "10.0.0.1"));
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 2; i++) {
            service.recordFailure("romashka", "10.0.0.1");
        }
        service.recordSuccess("romashka", "10.0.0.1");
        service.recordFailure("romashka", "10.0.0.1");
        service.recordFailure("romashka", "10.0.0.1");
        assertEquals(Duration.ZERO, service.blockedFor("romashka", "10.0.0.1"));
    }
}
