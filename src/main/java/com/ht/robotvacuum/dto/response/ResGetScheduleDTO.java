package com.ht.robotvacuum.dto.response;

import java.time.LocalTime;
import java.util.List;

public record ResGetScheduleDTO(long id, LocalTime time, boolean enabled, long robotId,
                                List<ResGetRepeatScheduleDTO> repeatSchedules) {
}
