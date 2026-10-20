package com.ht.robotvacuum.security;

import com.ht.robotvacuum.config.JwtProperties;
import com.ht.robotvacuum.dto.response.ResTokenDTO;
import com.ht.robotvacuum.mapper.AuthMapper;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final UserRepository users;
    private final TokenService tokens;
    private final JwtProperties properties;
    private final Clock clock;
    private final AuthMapper mapper;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ResTokenDTO login(long userId) {
        User user = users.findLockedById(userId).orElseThrow(this::invalidToken);
        user.setRefreshTokenExpiresAt(clock.instant().plusSeconds(properties.refreshTokenTtlSeconds()));
        return rotate(user);
    }

    @Transactional
    public ResTokenDTO refresh(String rawToken) {
        String hash = hash(rawToken);
        User user = findLockedUser(hash);
        if (!hash.equals(user.getRefreshToken()) || user.getRefreshTokenExpiresAt() == null
                || !user.getRefreshTokenExpiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }
        // Preserve the original login deadline; refresh does not extend the session.
        return rotate(user);
    }

    @Transactional
    public void logout(long userId) {
        users.findLockedById(userId).ifPresent(user -> {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
        });
    }

    private User findLockedUser(String hash) {
        return users.findIdByRefreshToken(hash).flatMap(users::findLockedById).orElseThrow(this::invalidToken);
    }

    private ResTokenDTO rotate(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setRefreshToken(hash(raw));
        return new ResTokenDTO(tokens.createAccessToken(user), raw, "Bearer", properties.accessTokenTtlSeconds(),
                Math.max(0, Duration.between(clock.instant(), user.getRefreshTokenExpiresAt()).getSeconds()),
                mapper.toAccount(user));
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid or expired refresh token");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
