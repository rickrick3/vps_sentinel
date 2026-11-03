package com.expertiz.sentinel.core.shared.text;

import java.util.regex.Pattern;

/**
 * Forme d'un email (spec §10.3), vérifiée sur la valeur déjà normalisée : {@code local@domaine}, le domaine contenant au
 * moins un point, sans espace ni caractère de contrôle, 254 caractères au plus.
 */
public final class EmailAddress {

    public static final int MAX_LENGTH = 254;

    private static final Pattern FORM =
            Pattern.compile("[^@\\s\\p{Cntrl}]+@[^@\\s\\p{Cntrl}.]+(\\.[^@\\s\\p{Cntrl}.]+)+");

    private EmailAddress() {}

    public static boolean isValid(String email) {
        return email != null && email.length() <= MAX_LENGTH && FORM.matcher(email).matches();
    }
}
