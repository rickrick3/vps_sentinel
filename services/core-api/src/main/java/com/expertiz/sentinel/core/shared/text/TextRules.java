package com.expertiz.sentinel.core.shared.text;

/** Règle générale des chaînes (spec §10.3) : espaces retirés aux extrémités, chaîne vide équivalente à {@code null}. */
public final class TextRules {

    private TextRules() {}

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
