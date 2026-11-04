package com.expertiz.sentinel.core.identity.internal.model;

import static com.expertiz.sentinel.core.identity.internal.model.TemporaryPasswordGenerator.ALPHABET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Mots de passe temporaires du spec §8.6 : 16 caractères d'un alphabet de 56 symboles sans ambiguïté, via SecureRandom. */
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(new SecureRandom());

    @Test
    void alphabetHas56DistinctSymbolsWithoutAmbiguous() {
        assertThat(ALPHABET.chars().distinct().count()).isEqualTo(56);
        assertThat(ALPHABET).hasSize(56).doesNotContainAnyWhitespaces().doesNotContain("0", "O", "o", "1", "l", "I");
    }

    @Test
    void generates16AlphabetChars() {
        Set<String> drawn = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(16);
            assertThat(password.chars()).allMatch(c -> ALPHABET.indexOf(c) >= 0);
            drawn.add(password);
        }
        assertThat(drawn).hasSize(1000);
    }

    @Test
    void everySymbolIsDrawnUniformly() {
        Map<Character, Integer> counts = new HashMap<>();
        int draws = 1250;
        for (int i = 0; i < draws; i++) {
            for (char c : generator.generate().toCharArray()) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        // 20 000 caractères : environ 357 par symbole, écart type proche de 19. Une marge de 30 % reste à plus de 5 écarts types.
        double expected = draws * 16.0 / ALPHABET.length();
        assertThat(counts).hasSize(ALPHABET.length());
        assertThat(counts.values()).allSatisfy(n -> assertThat(n).isBetween((int) (expected * 0.7), (int) (expected * 1.3)));
    }

    @Test
    void drawsOnlyFromTheInjectedSecureRandom() throws NoSuchAlgorithmException {
        // Deux sources initialisées avec la même graine produisent la même suite : le générateur n'utilise aucune autre source.
        List<String> first = sequence(seeded());
        List<String> second = sequence(seeded());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void satisfiesThePasswordPolicy() {
        assertThatCode(() -> PasswordPolicy.check("password", generator.generate(), "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresASecureRandom() {
        assertThatThrownBy(() -> new TemporaryPasswordGenerator(null)).isInstanceOf(NullPointerException.class);
    }

    private static SecureRandom seeded() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed("graine-de-test".getBytes(StandardCharsets.UTF_8));
        return random;
    }

    private static List<String> sequence(SecureRandom random) {
        TemporaryPasswordGenerator seededGenerator = new TemporaryPasswordGenerator(random);
        return IntStream.range(0, 5).mapToObj(i -> seededGenerator.generate()).toList();
    }
}
