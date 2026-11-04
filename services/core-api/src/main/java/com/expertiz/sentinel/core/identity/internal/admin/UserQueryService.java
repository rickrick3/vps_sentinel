package com.expertiz.sentinel.core.identity.internal.admin;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.PageRequests;
import com.expertiz.sentinel.core.shared.web.SortSpec;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture des utilisateurs (spec §10.1, §10.2) : filtres {@code q}, {@code role}, {@code status}, tri en liste blanche. */
@Service
public class UserQueryService {

    public static final SortSpec SORT = new SortSpec(
            Map.of("fullName", "fullName", "email", "email", "createdAt", "createdAt", "lastLoginAt", "lastLoginAt"),
            "fullName,asc");

    private static final int QUERY_MAX = 254;

    private final UserRepository users;

    UserQueryService(UserRepository users) {
        this.users = users;
    }

    /** {@code q} cherche dans le nom et l'email, sans tenir compte de la casse ; ses jokers SQL sont pris littéralement. */
    @Transactional(readOnly = true)
    public Page<User> list(String q, String role, String status, Integer page, Integer size, String sort) {
        List<FieldViolation> violations = new ArrayList<>();
        String query = TextRules.clean(q);
        if (query != null && query.codePointCount(0, query.length()) > QUERY_MAX) {
            violations.add(new FieldViolation("q", QUERY_MAX + " caractères maximum"));
        }
        RoleCode roleFilter = parse(RoleCode.values(), role, "role", "rôle inconnu", violations);
        UserStatus statusFilter = parse(UserStatus.values(), status, "status", "statut inconnu", violations);
        if (!violations.isEmpty()) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs filtres sont invalides.", violations);
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORT);
        // Départage par id : une page ne dépend pas de l'ordre arbitraire des égalités.
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
        return users.findAll(filter(query, roleFilter, statusFilter), stable);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ProblemType.NOT_FOUND, "Utilisateur introuvable."));
    }

    private static Specification<User> filter(String query, RoleCode role, UserStatus status) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                String pattern = "%" + escapeLike(Fingerprints.normalizeIdentifier(query)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                        cb.like(root.get("email"), pattern, '\\')));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role").get("code"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static <E extends Enum<E>> E parse(E[] values, String raw, String field, String message, List<FieldViolation> violations) {
        String clean = TextRules.clean(raw);
        if (clean == null) {
            return null;
        }
        E found = Arrays.stream(values).filter(value -> value.name().equals(clean)).findFirst().orElse(null);
        if (found == null) {
            violations.add(new FieldViolation(field, message));
        }
        return found;
    }
}
