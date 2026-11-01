package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.mqtt.MqttCommandDTO;
import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.model.enums.RobotCommandAction;
import com.ht.robotvacuum.model.enums.RobotMode;
import com.ht.robotvacuum.model.enums.RobotStatus;
import com.ht.robotvacuum.model.enums.TaskStatus;
import com.ht.robotvacuum.mqtt.MqttPublisher;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.ScheduleRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCleaningService {

    private final ScheduleRepository schedules;
    private final RobotRepository robots;
    private final TaskRepository tasks;
    private final RobotStateService states;
    private final MqttPublisher publisher;
    private final Clock clock;

    @Transactional
    public boolean execute(long scheduleId, ZonedDateTime dueAt) {
        // Match ScheduleService's lock order so edits/unpairing cannot race the decision.
        var schedule = schedules.findLockedById(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled() || !schedule.getTime().equals(dueAt.toLocalTime())
                || schedule.getRepeatSchedules().stream()
                    .noneMatch(day -> day.getDayOfWeek() == dueAt.getDayOfWeek().getValue())) {
            return false;
        }
        var robot = robots.findLockedById(schedule.getRobot().getId()).orElse(null);
        var now = clock.instant();
        if (robot == null || robot.getUser() == null
                || !now.atZone(CleaningScheduler.ZONE).truncatedTo(ChronoUnit.MINUTES).equals(dueAt)) {
            return false;
        }
        var state = states.getLatest(robot.getId());
        if (state.isEmpty()) {
            log.info("Skipped scheduled cleaning: no telemetry (scheduleId={}, robotId={})",
                    scheduleId, robot.getId());
            return false;
        }
        if (state.get().isStale()) {
            log.info("Skipped scheduled cleaning: stale telemetry (scheduleId={}, robotId={}, sentAt={}, receivedAt={}, now={})",
                    scheduleId, robot.getId(), state.get().getSentAt(), state.get().getReceivedAt(), now);
            return false;
        }
        if (state.get().getStatus() != RobotStatus.IDLE) {
            log.info("Skipped scheduled cleaning: robot is not IDLE (scheduleId={}, robotId={}, status={})",
                    scheduleId, robot.getId(), state.get().getStatus());
            return false;
        }
        if (tasks.existsByRobotIdAndStatus(robot.getId(), TaskStatus.RUNNING)) {
            log.info("Skipped scheduled cleaning: RUNNING task exists (scheduleId={}, robotId={})",
                    scheduleId, robot.getId());
            return false;
        }
        if (tasks.existsByRobotIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                robot.getId(), dueAt.toInstant(), dueAt.plusMinutes(1).toInstant())) {
            log.info("Skipped scheduled cleaning: task already started in this minute (scheduleId={}, robotId={}, dueAt={})",
                    scheduleId, robot.getId(), dueAt);
            return false;
        }

        Task task = new Task();
        task.setRobot(robot);
        task.setStartedAt(now);
        task.setStatus(TaskStatus.RUNNING);
        // Flush first to obtain taskId and detect DB constraints before sending to the device.
        task = tasks.saveAndFlush(task);
        var issuedAt = clock.instant();
        var command = new MqttCommandDTO(UUID.randomUUID(), RobotCommandAction.START, RobotMode.AUTO,
                null, issuedAt, issuedAt.plusSeconds(15), task.getId());
        // A publish failure rolls back this task. MQTT and the DB are not one atomic resource.
        publisher.publishCommand(robot.getId(), command);
        log.info("Published scheduled cleaning (scheduleId={}, robotId={}, taskId={})",
                scheduleId, robot.getId(), task.getId());
        return true;
    }
}
