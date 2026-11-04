package com.expertiz.sentinel.core.identity.internal.model;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Mots de passe temporaires (spec §8.6) : 16 caractères tirés uniformément ({@link SecureRandom#nextInt(int)}, sans
 * biais de modulo) d'un alphabet de 56 symboles sans caractères ambigus, soit environ 93 bits d'entropie.
 */
public final class TemporaryPasswordGenerator {

    /** Sans {@code 0}, {@code O}, {@code o}, {@code 1}, {@code l} ni {@code I}. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    public static final int LENGTH = 16;

    private final SecureRandom random;

    public TemporaryPasswordGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String generate() {
        char[] password = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            password[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(password);
    }
}
