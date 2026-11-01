package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.mqtt.MqttTaskEventDTO;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.enums.TaskStatus;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RobotTaskEventService {

    private final RobotRepository robots;
    private final TaskRepository tasks;
    private final ServiceValidation validation;
    private final Clock clock;

    @Transactional
    public void accept(long robotId, MqttTaskEventDTO event) {
        validation.requirePositiveId(robotId, "Robot id");
        Assert.notNull(event, "Task event is required");
        validation.validate(event);
        Assert.isTrue(event.status() == TaskStatus.COMPLETED, "Only completion events are supported");
        Assert.isTrue(!event.finishedAt().isAfter(clock.instant().plusSeconds(5)),
                "Task finish time is too far in the future");
        robots.findLockedById(robotId).orElseThrow(() -> new ResourceNotFoundException("Robot", robotId));
        var task = tasks.findById(event.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", event.taskId()));
        Assert.isTrue(task.getRobot().getId() == robotId, "Task does not belong to this robot");
        Assert.isTrue(!event.finishedAt().isBefore(task.getStartedAt()),
                "Task finish time must not precede its start time");
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return; // MQTT QoS 1 redelivery must not overwrite an already completed task.
        }
        task.setFinishedAt(event.finishedAt());
        task.setStatus(TaskStatus.COMPLETED);
    }
}
