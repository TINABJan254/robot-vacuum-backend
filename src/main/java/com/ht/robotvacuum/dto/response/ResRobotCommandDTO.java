package com.ht.robotvacuum.dto.response;

import com.ht.robotvacuum.model.enums.RobotCommandAction;
import com.ht.robotvacuum.model.enums.RobotDirection;
import com.ht.robotvacuum.model.enums.RobotMode;

import java.time.Instant;
import java.util.UUID;

public record ResRobotCommandDTO(UUID commandId, long robotId, RobotCommandAction action,
                                RobotMode mode, RobotDirection direction, String status,
                                Instant issuedAt, Instant expiresAt) {
}
