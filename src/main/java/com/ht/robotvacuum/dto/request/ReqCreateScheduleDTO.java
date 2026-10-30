package com.ht.robotvacuum.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;
import java.util.List;

public record ReqCreateScheduleDTO(
        @NotNull @Positive Long robotId,
        @NotNull LocalTime time,
        @NotNull Boolean enabled,
        @NotNull List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek) {
}
