package com.ht.robotvacuum.dto.response;

import java.time.Instant;

public record ResUpdateTaskDTO(long id, String name, Instant startAt, long duration, String status, Instant createdAt, long robotId) {
}
