package com.ht.robotvacuum.dto.mqtt;

import com.ht.robotvacuum.model.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record MqttTaskEventDTO(@NotNull @Positive Long taskId,
                               @NotNull TaskStatus status,
                               @NotNull Instant finishedAt) {
}
