package com.ht.robotvacuum.event;

import com.ht.robotvacuum.dto.response.RobotTelemetrySnapshot;

/** Published after a newer telemetry reading has been stored for a robot. */
public record TelemetryUpdatedEvent(RobotTelemetrySnapshot snapshot) {
}
