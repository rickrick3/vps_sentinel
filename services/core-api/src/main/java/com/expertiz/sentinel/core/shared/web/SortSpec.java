package com.expertiz.sentinel.core.shared.web;

import java.util.Map;

/**
 * Liste blanche de tri d'un endpoint (spec §10.2) : nom du champ dans l'API → propriété triée, et tri par défaut
 * au format {@code champ,asc|desc}.
 */
public record SortSpec(Map<String, String> apiToProperty, String defaultSort) {

    public SortSpec {
        apiToProperty = Map.copyOf(apiToProperty);
    }
}
