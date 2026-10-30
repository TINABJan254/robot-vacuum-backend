package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.ResGetTaskDTO;
import com.ht.robotvacuum.dto.response.ResPageDTO;
import com.ht.robotvacuum.mapper.TaskMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.model.enums.TaskStatus;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.Assert;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final RobotRepository robotRepository;
    private final ServiceValidation validation;
    private final CrudAccess access;
    private final TaskMapper mapper;

    @Transactional
    public Task create(@NotNull Task task) {
        validation.requireNewId(task.getId());
        validation.validate(task);
        if (task.getStatus() == TaskStatus.RUNNING) {
            Assert.isTrue(task.getFinishedAt() == null, "A running task must not have a finish time");
        } else {
            Assert.notNull(task.getFinishedAt(), "A completed task requires a finish time");
            Assert.isTrue(!task.getFinishedAt().isBefore(task.getStartedAt()),
                    "Task finish time must not precede its start time");
        }
        long robotId = task.getRobot().getId();
        validation.requirePositiveId(robotId, "Robot id");
        Robot robot = requireRobot(robotId);

        Task created = new Task();
        created.setStartedAt(task.getStartedAt());
        created.setFinishedAt(task.getFinishedAt());
        created.setStatus(task.getStatus());
        created.setRobot(robot);
        return taskRepository.save(created);
    }

    public Task getById(@Positive long id) {
        return findTask(id);
    }

    public Page<Task> getAll(@NotNull Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    public Page<Task> getByRobotId(@Positive long robotId, @NotNull Pageable pageable) {
        requireRobot(robotId);
        return taskRepository.findByRobotId(robotId, pageable);
    }

    @Transactional
    public Task update(@NotNull Task task) {
        validation.requirePositiveId(task.getId(), "Task id");
        Task existing = findTask(task.getId());
        validation.validate(task);
        if (task.getStatus() == TaskStatus.RUNNING) {
            Assert.isTrue(task.getFinishedAt() == null, "A running task must not have a finish time");
        } else {
            Assert.notNull(task.getFinishedAt(), "A completed task requires a finish time");
            Assert.isTrue(!task.getFinishedAt().isBefore(task.getStartedAt()),
                    "Task finish time must not precede its start time");
        }
        long robotId = task.getRobot().getId();
        validation.requirePositiveId(robotId, "Robot id");
        Robot robot = requireRobot(robotId);

        existing.setStartedAt(task.getStartedAt());
        existing.setFinishedAt(task.getFinishedAt());
        existing.setStatus(task.getStatus());
        existing.setRobot(robot);
        return taskRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        taskRepository.delete(findTask(id));
    }

    // HTTP reads keep authorization and DTO mapping in the same read-only transaction.
    public ResGetTaskDTO getById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Task existing = this.getById(id);
        access.ownsRobot(actor, existing.getRobot());
        return mapper.toGet(existing);
    }

    public ResPageDTO<ResGetTaskDTO> getAll(@NotNull Pageable pageable, Authentication authentication) {
        var actor = access.actor(authentication);
        var page = actor.admin() ? this.getAll(pageable) : taskRepository.findByRobotUserId(actor.id(), pageable);
        return ResPageDTO.from(page.map(mapper::toGet));
    }

    public ResPageDTO<ResGetTaskDTO> getByRobotId(@Positive long robotId, @NotNull Pageable pageable,
                                                Authentication authentication) {
        access.robot(access.actor(authentication), robotId);
        return ResPageDTO.from(taskRepository.findByRobotId(robotId, pageable).map(mapper::toGet));
    }

    private Robot requireRobot(long robotId) {
        return robotRepository.findById(robotId)
                .orElseThrow(() -> new ResourceNotFoundException("Robot", robotId));
    }

    private Task findTask(long id) {
        validation.requirePositiveId(id, "Task id");
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }
}
