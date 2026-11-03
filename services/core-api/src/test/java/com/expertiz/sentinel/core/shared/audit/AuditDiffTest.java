package com.expertiz.sentinel.core.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDiffTest {

    @Test
    void onlyChangedKeys() {
        Map<String, Object> before = Map.of("name", "VPS-01", "region", "eu-west");
        Map<String, Object> after = Map.of("name", "VPS-02", "region", "eu-west");

        assertThat(AuditDiff.changes(before, after))
                .containsOnlyKeys("name")
                .containsEntry("name", Map.of("from", "VPS-01", "to", "VPS-02"));
    }

    @Test
    void nullToValueAndValueToNull() {
        Map<String, Object> before = new HashMap<>();
        before.put("hostname", null);
        before.put("publicIp", "203.0.113.10");
        Map<String, Object> after = new HashMap<>();
        after.put("hostname", "vps-01.example.com");
        after.put("publicIp", null);

        Map<String, Map<String, Object>> changes = AuditDiff.changes(before, after);

        assertThat(changes.get("hostname")).containsEntry("from", null).containsEntry("to", "vps-01.example.com");
        assertThat(changes.get("publicIp")).containsEntry("from", "203.0.113.10").containsEntry("to", null);
    }

    @Test
    void keyPresentOnOneSideOnlyCountsAsNullOnTheOther() {
        assertThat(AuditDiff.changes(Map.of(), Map.of("tags", "web")).get("tags"))
                .containsEntry("from", null)
                .containsEntry("to", "web");
    }

    @Test
    void noChangeGivesEmptyDiff() {
        assertThat(AuditDiff.changes(Map.of("a", 1), Map.of("a", 1))).isEmpty();
    }
}
