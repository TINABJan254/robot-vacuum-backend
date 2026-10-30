package com.ht.robotvacuum.dto.response;

import com.ht.robotvacuum.model.enums.TaskStatus;
import java.time.Instant;

public record ResGetTaskDTO(long id, Instant startedAt, TaskStatus status, Instant finishedAt, long robotId) {
}
