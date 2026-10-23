package com.ht.robotvacuum.dto.response;

import com.ht.robotvacuum.model.enums.RobotMode;
import com.ht.robotvacuum.model.enums.RobotStatus;
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
public class RobotTelemetrySnapshot {

    private long robotId;
    private int batteryPercent;
    private RobotStatus status;
    private RobotMode mode;
    private Instant sentAt;
    private Instant receivedAt;
    private boolean stale;
}
