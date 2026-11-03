package com.expertiz.sentinel.core.shared.web;

import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Paramètres {@code page}, {@code size} et {@code sort} des listes, validés selon le spec §10.2. */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {}

    public static Pageable of(Integer page, Integer size, String sort, SortSpec spec) {
        List<FieldViolation> violations = new ArrayList<>();
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            violations.add(new FieldViolation("page", "doit être supérieur ou égal à 0"));
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            violations.add(new FieldViolation("size", "doit être compris entre 1 et " + MAX_SIZE));
        }
        Sort order = parseSort(sort == null || sort.isBlank() ? spec.defaultSort() : sort, spec, violations);
        if (!violations.isEmpty()) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Paramètres de pagination ou de tri invalides.", violations);
        }
        return PageRequest.of(pageNumber, pageSize, order);
    }

    private static Sort parseSort(String sort, SortSpec spec, List<FieldViolation> violations) {
        String[] parts = sort.split(",", -1);
        String property = spec.apiToProperty().get(parts[0].strip());
        String direction = parts.length == 2 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
        if (parts.length > 2 || property == null || !(direction.equals("asc") || direction.equals("desc"))) {
            violations.add(new FieldViolation("sort", "tri non autorisé ; format attendu : champ,asc ou champ,desc"));
            return Sort.unsorted();
        }
        return Sort.by(direction.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, property);
    }
}
