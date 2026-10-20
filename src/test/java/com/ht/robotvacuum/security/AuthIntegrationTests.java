package com.ht.robotvacuum.security;

import com.ht.robotvacuum.config.JwtProperties;
import com.ht.robotvacuum.dto.response.ResTokenDTO;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.model.enums.Role;
import com.ht.robotvacuum.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "app.security.jwt.base64-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
@Import(AuthIntegrationTests.TestConfig.class)
class AuthIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RefreshTokenService refreshTokens;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtProperties properties;
    @Autowired private MutableClock clock;

    @BeforeEach
    void reset() {
        // No test transaction: the HTTP/service operations must commit independently.
        users.deleteAllInBatch();
        clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    @Test
    void registerLoginAccountRefreshLogoutWorksWithRealDatabaseAndJwt() throws Exception {
        JsonNode registered = body(postJson("/auth/register", Map.of("name", "Owner", "username", "owner",
                "password", "example-password", "role", "ADMIN", "refreshToken", "client-supplied"), 201));
        assertEquals("USER", registered.get("role").asText());
        assertFalse(registered.has("password"));
        assertFalse(registered.has("refreshToken"));
        User user = users.findByUsername("owner").orElseThrow();
        assertTrue(passwordEncoder.matches("example-password", user.getPassword()));
        assertNull(user.getRefreshToken());
        JsonNode login = login("owner", "example-password");
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();
        assertEquals("Bearer", login.get("tokenType").asText());
        assertEquals(900, login.get("expiresIn").asLong());
        assertEquals(604800, login.get("refreshExpiresIn").asLong());
        assertEquals(43, refresh.length());
        User loggedIn = users.findById(user.getId()).orElseThrow();
        assertEquals(sha256(refresh), loggedIn.getRefreshToken());
        assertNotEquals(refresh, loggedIn.getRefreshToken());
        Instant deadline = loggedIn.getRefreshTokenExpiresAt();
        Jwt jwt = jwtDecoder.decode(access);
        assertEquals(Long.toString(user.getId()), jwt.getSubject());
        assertEquals("owner", jwt.getClaimAsString("username"));
        assertEquals("access", jwt.getClaimAsString("token_use"));
        assertEquals(List.of("USER"), jwt.getClaimAsStringList("roles"));
        assertNotNull(jwt.getId());
        assertFalse(jwt.getClaims().containsKey("password"));
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("owner"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("refreshToken").doesNotExist());

        JsonNode rotated = body(postJson("/auth/refresh", Map.of("refreshToken", refresh), 200));
        String nextRefresh = rotated.get("refreshToken").asText();
        assertNotEquals(refresh, nextRefresh);
        assertNotEquals(access, rotated.get("accessToken").asText());
        assertEquals(deadline, users.findById(user.getId()).orElseThrow().getRefreshTokenExpiresAt());
        postJson("/auth/refresh", Map.of("refreshToken", refresh), 401);
        logout(access);
        User loggedOut = users.findById(user.getId()).orElseThrow();
        assertNull(loggedOut.getRefreshToken());
        assertNull(loggedOut.getRefreshTokenExpiresAt());
        postJson("/auth/refresh", Map.of("refreshToken", nextRefresh), 401);
        logout(access);
        // Logout revokes refresh only; an existing JWT remains valid until expiry.
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void newLoginReplacesRefreshAndLogoutRevokesTheCurrentTokenForAuthenticatedUser() throws Exception {
        register();
        JsonNode firstLogin = login("owner", "example-password");
        String old = firstLogin.get("refreshToken").asText();
        String current = login("owner", "example-password").get("refreshToken").asText();
        postJson("/auth/refresh", Map.of("refreshToken", old), 401);
        // Like jobhunter: any still-valid access token identifies the account to log out.
        logout(firstLogin.get("accessToken").asText());
        postJson("/auth/refresh", Map.of("refreshToken", current), 401);
    }

    @Test
    void expiredAccessMustBeRefreshedBeforeAuthenticatedLogout() throws Exception {
        register();
        JsonNode login = login("owner", "example-password");
        clock.now = clock.now.plusSeconds(901);
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        assertNotNull(users.findByUsername("owner").orElseThrow().getRefreshToken());
        JsonNode rotated = body(postJson("/auth/refresh",
                Map.of("refreshToken", login.get("refreshToken").asText()), 200));
        assertEquals(604800 - 901, rotated.get("refreshExpiresIn").asLong());
        logout(rotated.get("accessToken").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-valid-jwt"})
    void logoutWithoutValidAuthenticationDoesNotClearToken(String access) throws Exception {
        register();
        login("owner", "example-password");
        String hash = users.findByUsername("owner").orElseThrow().getRefreshToken();
        var request = post("/api/v1/auth/logout");
        if (!access.isEmpty()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + access);
        }
        mvc.perform(request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("status").value(401))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertEquals(hash, users.findByUsername("owner").orElseThrow().getRefreshToken());
    }

    @Test
    void logoutOnlyClearsTheUserIdentifiedByAccessToken() throws Exception {
        register();
        String ownerAccess = login("owner", "example-password").get("accessToken").asText();
        postJson("/auth/register", Map.of("name", "Other", "username", "other", "password", "example-password"), 201);
        JsonNode otherLogin = login("other", "example-password");
        User other = users.findByUsername("other").orElseThrow();
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccess)
                        .param("userId", Long.toString(other.getId())))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertNull(users.findByUsername("owner").orElseThrow().getRefreshToken());
        assertEquals(other.getRefreshToken(), users.findById(other.getId()).orElseThrow().getRefreshToken());
        postJson("/auth/refresh", Map.of("refreshToken", otherLogin.get("refreshToken").asText()), 200);
    }

    @Test
    void refreshCannotExtendTheOriginalLoginDeadline() throws Exception {
        register();
        String refresh = login("owner", "example-password").get("refreshToken").asText();
        clock.now = clock.now.plusSeconds(604800);
        postJson("/auth/refresh", Map.of("refreshToken", refresh), 401);
    }

    @Test
    void refreshUsesTheCurrentDatabaseRole() throws Exception {
        register();
        JsonNode login = login("owner", "example-password");
        User user = users.findByUsername("owner").orElseThrow();
        user.setRole(Role.ADMIN);
        users.saveAndFlush(user);
        JsonNode refreshed = body(postJson("/auth/refresh",
                Map.of("refreshToken", login.get("refreshToken").asText()), 200));
        assertEquals(List.of("ADMIN"), jwtDecoder.decode(refreshed.get("accessToken").asText())
                .getClaimAsStringList("roles"));
        // Already issued JWT retains its original role until it expires.
        assertEquals(List.of("USER"), jwtDecoder.decode(login.get("accessToken").asText())
                .getClaimAsStringList("roles"));
    }

    @Test
    void concurrentRefreshAllowsExactlyOneCommittedRotation() throws Exception {
        register();
        String refresh = login("owner", "example-password").get("refreshToken").asText();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> refreshAfterSignal(start, refresh));
            var second = executor.submit(() -> refreshAfterSignal(start, refresh));
            start.countDown();
            ResTokenDTO a = first.get(20, TimeUnit.SECONDS);
            ResTokenDTO b = second.get(20, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null), "Only one rotation may succeed");
            ResTokenDTO winner = a != null ? a : b;
            assertEquals(sha256(winner.refreshToken()), users.findByUsername("owner").orElseThrow().getRefreshToken());
            assertNotNull(refreshTokens.refresh(winner.refreshToken()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void userCannotAccessAdminRouteButAdminCan() throws Exception {
        register();
        String userAccess = login("owner", "example-password").get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccess))
                .andExpect(status().isForbidden()).andExpect(jsonPath("status").value(403));
        User user = users.findByUsername("owner").orElseThrow();
        user.setRole(Role.ADMIN);
        users.saveAndFlush(user);
        String adminAccess = login("owner", "example-password").get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccess))
                .andExpect(status().isOk()).andExpect(content().string("admin-ok"));
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccess))
                .andExpect(status().isOk());
    }

    @Test
    void publicWhitelistDoesNotExposeOtherPathsOrMethods() throws Exception {
        mvc.perform(get("/api/v1/auth/account")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("status").value(401))
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsDoNotRevealWhetherAUserExists() throws Exception {
        register();
        JsonNode wrongPassword = body(postJson("/auth/login",
                Map.of("username", "owner", "password", "incorrect"), 401));
        JsonNode missingUser = body(postJson("/auth/login",
                Map.of("username", "missing", "password", "incorrect"), 401));
        assertEquals(wrongPassword.get("message"), missingUser.get("message"));
        assertFalse(wrongPassword.toString().contains("incorrect"));
        assertFalse(missingUser.toString().contains("missing"));
        assertNull(users.findByUsername("owner").orElseThrow().getRefreshToken());
    }

    @Test
    void invalidRegistrationAndTokenBodiesAreRejectedWithoutLeakingInput() throws Exception {
        postJson("/auth/register", Map.of("name", "Owner", "username", "owner", "password", " "), 400);
        postJson("/auth/register", Map.of("name", "Owner", "username", "owner", "password", "secret", "id", 50), 400);
        postJson("/auth/register", Map.of("name", "Owner", "username", "owner", "password", "é".repeat(37)), 400);
        postJson("/auth/login", Map.of("username", "owner", "password", "é".repeat(37)), 400);
        postJson("/auth/refresh", Map.of(), 400);
        assertEquals(0, users.count());
        register();
        postJson("/auth/register", Map.of("name", "Owner", "username", "owner", "password", "example-password"), 409);
        assertEquals(1, users.count());
    }

    @Test
    void malformedRequestsKeepHttpStatusAndDoNotExposeRawPayload() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret-value\",BROKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains("secret-value")));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("secret-value"))
                .andExpect(status().isUnsupportedMediaType());
        register();
        String access = login("owner", "example-password").get("accessToken").asText();
        mvc.perform(put("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists(HttpHeaders.ALLOW));
    }

    @Test
    void refreshTokenAndTamperedJwtCannotBeUsedAsAccessToken() throws Exception {
        register();
        JsonNode login = login("owner", "example-password");
        String access = login.get("accessToken").asText();
        String tampered = access.substring(0, access.lastIndexOf('.') + 1) + "A".repeat(86);
        for (String token : List.of(login.get("refreshToken").asText(), tampered, "not-a-jwt")) {
            mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "missing-exp", "token-use", "missing-type",
            "subject", "roles", "missing-roles", "roles-type", "future-nbf", "algorithm"})
    void jwtRejectsInvalidClaimsAndAlgorithms(String problem) throws Exception {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject("1").issuedAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(900)).claim("token_use", "access")
                .claim("roles", List.of("USER"));
        MacAlgorithm algorithm = MacAlgorithm.HS512;
        switch (problem) {
            case "issuer" -> claims.issuer("wrong-issuer");
            case "audience" -> claims.audience(List.of("other-api"));
            case "expired" -> claims.issuedAt(clock.instant().minusSeconds(60)).expiresAt(clock.instant().minusSeconds(1));
            case "missing-exp" -> claims.claims(values -> values.remove("exp"));
            case "token-use" -> claims.claim("token_use", "refresh");
            case "missing-type" -> claims.claims(values -> values.remove("token_use"));
            case "subject" -> claims.subject("anonymousUser");
            case "roles" -> claims.claim("roles", List.of("SUPER_ADMIN"));
            case "missing-roles" -> claims.claims(values -> values.remove("roles"));
            case "roles-type" -> claims.claim("roles", "ADMIN");
            case "future-nbf" -> claims.notBefore(clock.instant().plusSeconds(120));
            case "algorithm" -> algorithm = MacAlgorithm.HS256;
            default -> fail("Unknown test case");
        }
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims.build()))
                .getTokenValue();
        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
        mvc.perform(get("/api/v1/auth/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private ResTokenDTO refreshAfterSignal(CountDownLatch start, String refresh) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return refreshTokens.refresh(refresh);
        } catch (BadCredentialsException expected) {
            return null;
        }
    }

    private void register() throws Exception {
        postJson("/auth/register", Map.of("name", "Owner", "username", "owner", "password", "example-password"), 201);
    }

    private JsonNode login(String username, String password) throws Exception {
        return body(postJson("/auth/login", Map.of("username", username, "password", password), 200));
    }

    private void logout(String accessToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    private MvcResult postJson(String path, Object body, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        if (expectedStatus == 204) {
            assertTrue(result.getResponse().getContentAsString().isEmpty());
        }
        if (expectedStatus == 200 || expectedStatus == 201) {
            assertEquals("no-store", result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL));
        }
        return result;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean AdminProbe adminProbe() { return new AdminProbe(); }
    }

    @RestController
    static class AdminProbe {
        @GetMapping("/api/v1/admin/probe") String probe() { return "admin-ok"; }
    }

    static class MutableClock extends Clock {
        volatile Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
