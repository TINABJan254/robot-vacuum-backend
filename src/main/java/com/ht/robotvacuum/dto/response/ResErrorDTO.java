package com.ht.robotvacuum.dto.response;

import java.time.Instant;
import java.util.List;

public record ResErrorDTO(Instant timestamp, int status, String message, String path,
                          List<ResFieldErrorDTO> fieldErrors) {
}
