package com.ht.robotvacuum.security;

import com.ht.robotvacuum.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtPropertiesTests {

    private final String testSecret = Base64.getEncoder().encodeToString(new byte[64]);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("app.security.jwt.issuer=test-issuer", "app.security.jwt.audience=test-api",
                        "app.security.jwt.access-token-ttl-seconds=900",
                        "app.security.jwt.refresh-token-ttl-seconds=604800");
    }

    @Test
    void validConfigurationBindsWithoutRevealingTheSecretInToString() {
        runner().withPropertyValues("app.security.jwt.base64-secret=" + testSecret).run(context -> {
            assertNull(context.getStartupFailure());
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertTrue(properties.isSecretValid());
            assertFalse(properties.toString().contains(testSecret));
        });
    }

    @Test
    void missingSecretPreventsStartup() {
        runner().run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!", "c2hvcnQ="})
    void missingMalformedAndShortSecretsPreventStartup(String secret) {
        runner().withPropertyValues("app.security.jwt.base64-secret=" + secret)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonPositiveTtlsPreventStartup(long ttl) {
        runner().withPropertyValues("app.security.jwt.base64-secret=" + testSecret,
                        "app.security.jwt.access-token-ttl-seconds=" + ttl)
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues("app.security.jwt.base64-secret=" + testSecret,
                        "app.security.jwt.refresh-token-ttl-seconds=" + ttl)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }
}
