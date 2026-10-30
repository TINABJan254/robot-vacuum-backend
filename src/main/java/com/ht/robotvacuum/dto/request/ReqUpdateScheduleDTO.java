package com.ht.robotvacuum.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record ReqUpdateScheduleDTO(
        LocalTime time,
        Boolean enabled,
        List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek) {
}
