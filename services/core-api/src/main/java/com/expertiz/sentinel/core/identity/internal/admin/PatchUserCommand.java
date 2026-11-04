package com.expertiz.sentinel.core.identity.internal.admin;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.NoControlCharsValidator;
import com.expertiz.sentinel.core.shared.text.TextRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code PATCH /users/{id}} (spec §10.1, §10.2). Lu sur l'objet JSON brut : un champ absent reste inchangé, un
 * {@code null} explicite est refusé (aucun champ n'est vidable), un champ inconnu aussi, et {@code version} est
 * obligatoire.
 */
public record PatchUserCommand(Optional<String> fullName, Optional<RoleCode> role, Optional<UserStatus> status, long version) {

    private static final Set<String> FIELDS = Set.of("fullName", "role", "status", "version");
    private static final int FULL_NAME_MAX = 120;
    private static final NoControlCharsValidator CONTROL_CHARS = new NoControlCharsValidator();

    /** Seuls un rôle ou un statut peuvent réduire le nombre de SUPER_ADMIN actifs. */
    public boolean changesRoleOrStatus() {
        return role.isPresent() || status.isPresent();
    }

    /** {@code body} : l'objet JSON tel que désérialisé, une clé présente avec une valeur {@code null} comprise. */
    public static PatchUserCommand parse(Map<String, Object> body) {
        List<FieldViolation> violations = new ArrayList<>();
        String fullName = null;
        RoleCode role = null;
        UserStatus status = null;
        Long version = null;
        for (Map.Entry<String, Object> property : body.entrySet()) {
            String field = property.getKey();
            Object value = property.getValue();
            if (!FIELDS.contains(field)) {
                violations.add(new FieldViolation(field, "champ inconnu ou non modifiable"));
            } else if (field.equals("version")) {
                if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 0) {
                    version = ((Number) value).longValue();
                } else {
                    violations.add(new FieldViolation(field, "doit être un entier positif ou nul"));
                }
            } else if (value == null) {
                violations.add(new FieldViolation(field, "ne doit pas être null"));
            } else if (!(value instanceof String text)) {
                violations.add(new FieldViolation(field, "doit être une chaîne de caractères"));
            } else {
                switch (field) {
                    case "fullName" -> fullName = fullName(text, violations);
                    case "role" -> role = valueOf(RoleCode.values(), text, field, "rôle inconnu", violations);
                    case "status" -> status = valueOf(UserStatus.values(), text, field, "statut inconnu", violations);
                    default -> throw new IllegalStateException(field);
                }
            }
        }
        if (!body.containsKey("version")) {
            violations.add(new FieldViolation("version", "est obligatoire"));
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs champs sont invalides.", violations);
        }
        return new PatchUserCommand(Optional.ofNullable(fullName), Optional.ofNullable(role), Optional.ofNullable(status), version);
    }

    private static String fullName(String raw, List<FieldViolation> violations) {
        String clean = TextRules.clean(raw);
        if (clean == null) {
            violations.add(new FieldViolation("fullName", "ne doit pas être vide"));
        } else if (!CONTROL_CHARS.isValid(clean, null)) {
            violations.add(new FieldViolation("fullName", "ne doit pas contenir de caractères de contrôle"));
        } else if (clean.codePointCount(0, clean.length()) > FULL_NAME_MAX) {
            violations.add(new FieldViolation("fullName", FULL_NAME_MAX + " caractères maximum"));
        } else {
            return clean;
        }
        return null;
    }

    private static <E extends Enum<E>> E valueOf(E[] values, String raw, String field, String message, List<FieldViolation> violations) {
        Optional<E> found = Arrays.stream(values).filter(value -> value.name().equals(raw)).findFirst();
        if (found.isEmpty()) {
            violations.add(new FieldViolation(field, message));
        }
        return found.orElse(null);
    }
}
