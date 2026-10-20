package com.ht.robotvacuum.security;

import com.ht.robotvacuum.config.JwtProperties;
import com.ht.robotvacuum.model.enums.Role;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.List;

public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenValidator(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Object claim = jwt.getClaims().get("roles");
            boolean valid = "access".equals(jwt.getClaimAsString("token_use"))
                    && Long.parseLong(jwt.getSubject()) > 0
                    && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(clock.instant())
                    && jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                    && claim instanceof List<?> roles && !roles.isEmpty()
                    && roles.stream().allMatch(role -> role instanceof String value && isRole(value));
            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Invalid claim types and missing claims are authentication failures.
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid access token claims", null));
    }

    private boolean isRole(String value) {
        return Role.USER.name().equals(value) || Role.ADMIN.name().equals(value);
    }
}
