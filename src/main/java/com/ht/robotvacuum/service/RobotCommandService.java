package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.mqtt.MqttCommandDTO;
import com.ht.robotvacuum.dto.request.ReqRobotCommandDTO;
import com.ht.robotvacuum.dto.response.ResRobotCommandDTO;
import com.ht.robotvacuum.model.enums.RobotCommandAction;
import com.ht.robotvacuum.model.enums.RobotMode;
import com.ht.robotvacuum.mqtt.MqttPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RobotCommandService {

    private static final Duration COMMAND_TTL = Duration.ofSeconds(15);

    private final CrudAccess access;
    private final ServiceValidation validation;
    private final MqttPublisher publisher;
    private final Clock clock;

    public ResRobotCommandDTO send(long robotId, ReqRobotCommandDTO input, Authentication authentication) {
        var actor = access.actorFromToken(authentication);
        access.robot(actor, robotId);
        if (input == null) {
            throw new IllegalArgumentException("Command is required");
        }
        validation.validate(input);
        var mode = input.getMode();
        if (input.getAction() == RobotCommandAction.START && mode == null) {
            mode = RobotMode.MANUAL;
        }
        var issuedAt = clock.instant();
        var command = new MqttCommandDTO(UUID.randomUUID(), input.getAction(), mode,
                input.getDirection(), issuedAt, issuedAt.plus(COMMAND_TTL));

        publisher.publishCommand(robotId, command);
        return new ResRobotCommandDTO(command.commandId(), robotId, command.action(), command.mode(),
                command.direction(), "PUBLISHED", command.issuedAt(), command.expiresAt());
    }
}
