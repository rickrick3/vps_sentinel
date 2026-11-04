package com.expertiz.sentinel.core.shared.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** Identifiants UUIDv7 générés par l'application : l'id est connu avant l'insertion (spec §9.1). */
public final class UuidV7 {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7() {}

    public static UUID next() {
        return GENERATOR.generate();
    }
}
