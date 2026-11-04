package com.expertiz.sentinel.core.shared.secrets;

import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class SecretsConfig {

    /** Secrets lus dans {@code sentinel.secrets.dir} ; des secrets éphémères ne sont admis qu'en profil dev. */
    @Bean
    SentinelSecrets sentinelSecrets(@Value("${sentinel.secrets.dir}") Path directory, Environment environment) {
        return new SentinelSecrets(directory, environment.matchesProfiles("dev"));
    }

    @Bean
    Fingerprints fingerprints(SentinelSecrets secrets) {
        return new Fingerprints(secrets.hmacKey());
    }
}
