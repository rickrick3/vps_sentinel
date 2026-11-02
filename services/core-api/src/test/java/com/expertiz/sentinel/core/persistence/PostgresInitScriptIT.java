package com.expertiz.sentinel.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.support.SentinelPostgres;
import org.junit.jupiter.api.Test;

class PostgresInitScriptIT {

    @Test
    void initScriptIsExecutedNotSourced() {
        assertThat(SentinelPostgres.shared().logs())
                .contains("running /docker-entrypoint-initdb.d/01-roles.sh")
                .doesNotContain("sourcing /docker-entrypoint-initdb.d/01-roles.sh");
    }
}
