package com.ht.robotvacuum.realtime;

import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.service.RobotStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorizes the WebSocket upgrade for {@code /api/v1/robots/{id}/telemetry/ws}.
 * The JWT itself was already validated by the security filter chain (Authorization header on the
 * handshake request); here we check robot ownership exactly like {@code GET /robots/{id}/telemetry}.
 */
@Component
@RequiredArgsConstructor
public class TelemetryHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROBOT_ID = "robotId";
    static final String TOKEN_EXPIRES_AT = "tokenExpiresAt";

    private static final Pattern PATH = Pattern.compile(".*/robots/([1-9][0-9]{0,17})/telemetry/ws/?");

    private final RobotStateService states;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Matcher match = PATH.matcher(request.getURI().getPath());
        if (!match.matches()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        long robotId = Long.parseLong(match.group(1));
        if (!(request.getPrincipal() instanceof JwtAuthenticationToken authentication)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            states.requireReadAccess(robotId, authentication);
        } catch (ResourceNotFoundException ex) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        } catch (AuthenticationException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (AccessDeniedException ex) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ROBOT_ID, robotId);
        Instant expiresAt = authentication.getToken().getExpiresAt();
        if (expiresAt != null) {
            attributes.put(TOKEN_EXPIRES_AT, expiresAt);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do.
    }
}
