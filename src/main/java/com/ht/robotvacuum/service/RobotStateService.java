package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.mqtt.MqttTelemetryDTO;
import com.ht.robotvacuum.dto.response.RobotTelemetrySnapshot;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RobotStateService {

    private static final Duration TELEMETRY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FUTURE_TOLERANCE = Duration.ofSeconds(5);

    private final RobotRepository robots;
    private final ServiceValidation validation;
    private final Clock clock;
    private final CrudAccess access;
    private final ConcurrentHashMap<Long, RobotTelemetrySnapshot> states = new ConcurrentHashMap<>();

    public void acceptTelemetry(long robotId, MqttTelemetryDTO telemetry) {
        validation.requirePositiveId(robotId, "Robot id");
        Assert.notNull(telemetry, "Telemetry is required");
        validation.validate(telemetry);
        Instant receivedAt = clock.instant();
        Assert.isTrue(!telemetry.getSentAt().isAfter(receivedAt.plus(FUTURE_TOLERANCE)),
                "Telemetry timestamp is too far in the future");
        if (!robots.existsById(robotId)) {
            throw new ResourceNotFoundException("Robot", robotId);
        }

        states.compute(robotId, (id, previous) -> {
            if (previous != null && !telemetry.getSentAt().isAfter(previous.getSentAt())) {
                return previous; // Duplicates/older readings do not refresh receivedAt.
            }
            return new RobotTelemetrySnapshot(id, telemetry.getBatteryPercent(), telemetry.getStatus(),
                    telemetry.getMode(), telemetry.getSentAt(), receivedAt, false);
        });
    }

    @Transactional(readOnly = true)
    public Optional<RobotTelemetrySnapshot> getLatest(long robotId, Authentication authentication) {
        access.robot(access.actor(authentication), robotId);
        return getLatest(robotId);
    }

    // Internal read. HTTP/WebSocket callers use the authorized overload above.
    public Optional<RobotTelemetrySnapshot> getLatest(long robotId) {
        validation.requirePositiveId(robotId, "Robot id");
        RobotTelemetrySnapshot snapshot = states.get(robotId);
        if (snapshot == null) {
            return Optional.empty();
        }
        Instant cutoff = clock.instant().minus(TELEMETRY_TIMEOUT);
        boolean stale = !snapshot.getSentAt().isAfter(cutoff) || !snapshot.getReceivedAt().isAfter(cutoff);
        return Optional.of(new RobotTelemetrySnapshot(snapshot.getRobotId(), snapshot.getBatteryPercent(),
                snapshot.getStatus(), snapshot.getMode(), snapshot.getSentAt(), snapshot.getReceivedAt(), stale));
    }
}
