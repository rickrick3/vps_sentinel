package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Tags d'un VPS (spec §10.3) : espaces retirés, minuscules, doublons supprimés, triés ; chacun de 1 à 50 caractères
 * {@code ^[a-z0-9][a-z0-9._-]*$} ; 20 au plus après dédoublonnage.
 */
public final class TagRules {

    public static final int MAX_TAGS = 20;
    public static final int MAX_LENGTH = 50;

    private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private TagRules() {}

    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        TreeSet<String> tags = new TreeSet<>();
        for (String value : raw) {
            String tag = normalizeOne(value);
            if (tag == null) {
                throw violation("tag invalide : 1 à " + MAX_LENGTH + " caractères parmi a-z, 0-9, « . », « _ » et « - », "
                        + "commençant par une lettre ou un chiffre");
            }
            tags.add(tag);
        }
        if (tags.size() > MAX_TAGS) {
            throw violation(MAX_TAGS + " tags maximum");
        }
        return List.copyOf(tags);
    }

    /** Forme normalisée d'un tag, ou {@code null} s'il est invalide ; sert aussi au filtre de la liste. */
    static String normalizeOne(String value) {
        if (value == null) {
            return null;
        }
        String tag = value.strip().toLowerCase(Locale.ROOT);
        return tag.length() <= MAX_LENGTH && TAG.matcher(tag).matches() ? tag : null;
    }

    private static ApiException violation(String message) {
        return new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs champs sont invalides.",
                List.of(new FieldViolation("tags", Objects.requireNonNull(message))));
    }
}
