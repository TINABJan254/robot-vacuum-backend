package com.ht.robotvacuum.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public long requireId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("Authentication required");
        }
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // A valid numeric subject is also enforced by the JWT validator.
        }
        throw new BadCredentialsException("Invalid access token");
    }
}
