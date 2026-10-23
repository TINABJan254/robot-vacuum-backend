package com.ht.robotvacuum.dto.mqtt;

import com.ht.robotvacuum.model.enums.RobotMode;
import com.ht.robotvacuum.model.enums.RobotStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MqttTelemetryDTO {

    @NotNull
    @Min(0)
    @Max(100)
    private Integer batteryPercent;

    @NotNull
    private RobotStatus status;

    private RobotMode mode;

    @NotNull
    private Instant sentAt;
}
