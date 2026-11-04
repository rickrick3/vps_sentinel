package com.expertiz.sentinel.core.identity.internal.session;

import static com.expertiz.sentinel.core.identity.internal.session.LoginThrottleService.delayFor;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Délai {@code min(30 s × 2^(n − seuil), 15 min)} à partir du seuil (spec §8.4). */
class ThrottleDelayTest {

    @Test
    void delays() {
        assertThat(delayFor(4, 5)).isZero();
        assertThat(List.of(5, 6, 7, 8, 9, 10, 30).stream().map(n -> delayFor(n, 5).toSeconds()))
                .containsExactly(30L, 60L, 120L, 240L, 480L, 900L, 900L);
        assertThat(delayFor(19, 20)).isZero();
        assertThat(delayFor(20, 20)).hasSeconds(30);
    }

    @Test
    void neverOverflows() {
        assertThat(delayFor(Integer.MAX_VALUE, 5)).isEqualTo(Duration.ofMinutes(15));
        assertThat(delayFor(0, 5)).isZero();
    }
}
