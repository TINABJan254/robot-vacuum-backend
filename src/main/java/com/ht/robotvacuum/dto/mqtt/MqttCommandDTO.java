package com.ht.robotvacuum.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ht.robotvacuum.model.enums.RobotCommandAction;
import com.ht.robotvacuum.model.enums.RobotDirection;
import com.ht.robotvacuum.model.enums.RobotMode;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MqttCommandDTO(UUID commandId, RobotCommandAction action,
                             RobotMode mode, RobotDirection direction,
                             Instant issuedAt, Instant expiresAt) {
}
