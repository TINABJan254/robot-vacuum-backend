package com.ht.robotvacuum.dto.response;

import java.time.Instant;

public record ResCreateAlertDTO(long id, String type, String message, Instant readAt, Instant createdAt, long robotId) {
}
