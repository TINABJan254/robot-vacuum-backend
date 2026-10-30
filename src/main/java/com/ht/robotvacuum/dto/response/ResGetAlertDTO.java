package com.ht.robotvacuum.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

public record ResGetAlertDTO(long id, String title, String message, Instant readAt, Instant createdAt,
                             @JsonIgnoreProperties({"pairCode", "userId"}) ResGetRobotDTO robot) {
}
