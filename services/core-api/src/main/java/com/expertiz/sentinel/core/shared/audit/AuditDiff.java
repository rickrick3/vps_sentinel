package com.expertiz.sentinel.core.shared.audit;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Différences entre deux états auditables, au format {@code {"champ": {"from": …, "to": …}}} (spec §9.5). */
public final class AuditDiff {

    private AuditDiff() {}

    public static Map<String, Map<String, Object>> changes(Map<String, ?> before, Map<String, ?> after) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        for (String key : keys) {
            Object from = before.get(key);
            Object to = after.get(key);
            if (!Objects.equals(from, to)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", from);
                change.put("to", to);
                changes.put(key, change);
            }
        }
        return changes;
    }
}
