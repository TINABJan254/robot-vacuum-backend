package com.ht.robotvacuum.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String base64Secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Positive long accessTokenTtlSeconds,
        @Positive long refreshTokenTtlSeconds) {

    @AssertTrue(message = "JWT_BASE64_SECRET must encode at least 64 random bytes in Base64")
    public boolean isSecretValid() {
        try {
            return base64Secret != null && Base64.getDecoder().decode(base64Secret).length >= 64;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED]";
    }
}
