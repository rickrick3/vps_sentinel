package com.expertiz.sentinel.core.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Réponse paginée (spec §10.2). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
