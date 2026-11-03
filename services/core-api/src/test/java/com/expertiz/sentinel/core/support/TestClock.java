package com.expertiz.sentinel.core.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge contrôlée par les tests (spec §13.1) : les expirations se testent sans attendre. */
public class TestClock extends Clock {

    private volatile Instant instant;

    public TestClock(Instant start) {
        this.instant = start;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("L'horloge applicative est en UTC");
    }
}
