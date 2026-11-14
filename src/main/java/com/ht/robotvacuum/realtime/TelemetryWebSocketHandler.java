package com.ht.robotvacuum.realtime;

import com.ht.robotvacuum.dto.response.RobotTelemetrySnapshot;
import com.ht.robotvacuum.event.RobotAccessChangedEvent;
import com.ht.robotvacuum.event.TelemetryUpdatedEvent;
import com.ht.robotvacuum.service.RobotStateService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes robot telemetry to the app over WebSocket ({@code /api/v1/robots/{id}/telemetry/ws}).
 *
 * <p>Messages (server -> app), one JSON object per text frame:
 * <pre>{"type":"telemetry","robotId":3,"data":{...RobotTelemetrySnapshot...}}</pre>
 * {@code data} is {@code null} when the backend has no reading for the robot yet.
 * The current snapshot is sent right after the connection opens, then on every new reading,
 * and once more when the reading becomes stale (robot offline).
 *
 * <p>Close codes: 4401 = access token expired (reconnect with a fresh token),
 * 4403 = robot ownership changed (reconnect to re-check access).
 */
@Slf4j
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "Access token expired");
    static final CloseStatus ACCESS_CHANGED = new CloseStatus(4403, "Robot access changed");

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final RobotStateService states;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** robotId -> (raw session id -> thread-safe decorated session). */
    private final Map<Long, Map<String, WebSocketSession>> sessionsByRobot = new ConcurrentHashMap<>();
    /** Last "stale" flag pushed per robot, so the offline transition is pushed exactly once. */
    private final Map<Long, Boolean> lastPushedStale = new ConcurrentHashMap<>();
    /** Sends happen off the MQTT inbound thread so a slow client cannot stall telemetry intake. */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "telemetry-ws-sender");
        thread.setDaemon(true);
        return thread;
    });

    public TelemetryWebSocketHandler(RobotStateService states, ObjectMapper mapper, Clock clock) {
        this.states = states;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    //  Connection lifecycle
    // ------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long robotId = robotId(session);
        if (robotId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessionsByRobot.computeIfAbsent(robotId, id -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        String initial = message(robotId, states.getLatest(robotId).orElse(null));
        sender.execute(() -> send(safe, initial));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long robotId = robotId(session);
        if (robotId == null) {
            return;
        }
        sessionsByRobot.computeIfPresent(robotId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
        if (!sessionsByRobot.containsKey(robotId)) {
            lastPushedStale.remove(robotId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // The channel is push-only; commands still go through POST /robots/{id}/commands.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Telemetry WebSocket transport error ({})", exception.getClass().getSimpleName());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ------------------------------------------------------------------
    //  Events
    // ------------------------------------------------------------------

    @EventListener
    public void onTelemetry(TelemetryUpdatedEvent event) {
        RobotTelemetrySnapshot snapshot = event.snapshot();
        long robotId = snapshot.getRobotId();
        if (!sessionsByRobot.containsKey(robotId)) {
            return;
        }
        lastPushedStale.put(robotId, false);
        broadcast(robotId, message(robotId, snapshot));
    }

    /** Owner changed: drop live subscriptions after commit; the app reconnects and is re-authorized. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onAccessChanged(RobotAccessChangedEvent event) {
        Map<String, WebSocketSession> sessions = sessionsByRobot.get(event.robotId());
        if (sessions != null) {
            sessions.values().forEach(session -> closeQuietly(session, ACCESS_CHANGED));
        }
    }

    /** Closes connections whose access token expired and pushes the online -> offline (stale) transition. */
    @Scheduled(fixedDelay = 5_000)
    public void sweep() {
        Instant now = clock.instant();
        sessionsByRobot.forEach((robotId, sessions) -> {
            sessions.values().forEach(session -> {
                Object expiresAt = session.getAttributes().get(TelemetryHandshakeInterceptor.TOKEN_EXPIRES_AT);
                if (expiresAt instanceof Instant expiry && !expiry.isAfter(now)) {
                    closeQuietly(session, TOKEN_EXPIRED);
                }
            });
            states.getLatest(robotId).ifPresent(snapshot -> {
                if (snapshot.isStale() && !Boolean.TRUE.equals(lastPushedStale.get(robotId))) {
                    lastPushedStale.put(robotId, true);
                    broadcast(robotId, message(robotId, snapshot));
                }
            });
        });
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void broadcast(long robotId, String json) {
        if (json == null) {
            return;
        }
        Map<String, WebSocketSession> sessions = sessionsByRobot.get(robotId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions.values()) {
            sender.execute(() -> send(session, json));
        }
    }

    private void send(WebSocketSession session, String json) {
        if (json == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException ex) {
            // Includes SessionLimitExceededException (slow client): the decorator closes the session.
            log.debug("Telemetry WebSocket send failed ({})", ex.getClass().getSimpleName());
            closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private String message(long robotId, RobotTelemetrySnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "telemetry");
        body.put("robotId", robotId);
        body.put("data", snapshot);
        try {
            return mapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            log.warn("Could not serialize telemetry for robot {}", robotId);
            return null;
        }
    }

    private static Long robotId(WebSocketSession session) {
        return session.getAttributes().get(TelemetryHandshakeInterceptor.ROBOT_ID) instanceof Long id ? id : null;
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException | RuntimeException ignored) {
            // Already closing.
        }
    }
}
