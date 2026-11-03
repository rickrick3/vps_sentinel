package com.expertiz.sentinel.core.identity.internal.model;

import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.List;

/**
 * Politique des mots de passe (spec §8.6), appliquée chaque fois qu'un mot de passe est défini : changement,
 * bootstrap. Les mots de passe temporaires la respectent par construction.
 *
 * <ul>
 *   <li><b>Longueur : 12 à 128 caractères</b>, comptés en points de code Unicode (un émoji compte pour un). La longueur
 *       est le facteur qui compte face à une attaque par force brute ; le maximum borne le coût du hachage Argon2.
 *   <li><b>Différent de l'email</b> du compte, comparé sous forme normalisée (espaces aux extrémités, NFKC, casse).
 *   <li><b>Aucune règle de composition</b> (majuscule, chiffre, symbole imposés) : elles poussent vers des motifs
 *       prévisibles sans gain réel, d'après NIST SP 800-63B. La liste de mots de passe courants est un point ouvert
 *       (spec §17).
 * </ul>
 *
 * Le mot de passe n'est ni nettoyé ni normalisé : il est haché tel que saisi. Les messages ne le reprennent jamais.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    /** Lève une {@code validation-error} sur {@code field} si le mot de passe ne respecte pas la politique. */
    public static void check(String field, String password, String email) {
        String value = password == null ? "" : password;
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH) {
            throw violation(field, MIN_LENGTH + " caractères minimum.");
        }
        if (length > MAX_LENGTH) {
            throw violation(field, MAX_LENGTH + " caractères maximum.");
        }
        if (email != null && Fingerprints.normalizeIdentifier(value).equals(Fingerprints.normalizeIdentifier(email))) {
            throw violation(field, "Le mot de passe doit être différent de l'email.");
        }
    }

    private static ApiException violation(String field, String message) {
        return new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs champs sont invalides.",
                List.of(new FieldViolation(field, message)));
    }
}
