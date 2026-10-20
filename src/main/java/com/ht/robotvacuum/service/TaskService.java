package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.mapper.TaskMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.Task;
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
        return taskRepository.save(prepareValues(task));
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
        Task candidate = prepareValues(task);
        existing.setName(candidate.getName());
        existing.setStartAt(candidate.getStartAt());
        existing.setDuration(candidate.getDuration());
        existing.setStatus(candidate.getStatus());
        existing.setRobot(candidate.getRobot());
        return taskRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        taskRepository.delete(findTask(id));
    }

    // These overloads keep authorization, persistence and DTO mapping in one transaction.
    @Transactional
    public ResCreateTaskDTO create(@NotNull Task input, Authentication authentication) {
        var actor = access.actor(authentication);
        validation.validate(input);
        access.lockRobots(actor, input.getRobot().getId());
        return mapper.toCreate(this.create(input));
    }

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

    @Transactional
    public ResUpdateTaskDTO update(@NotNull Task input, Authentication authentication) {
        var actor = access.actor(authentication);
        Task existing = lock(input.getId());
        validation.validate(input);
        access.lockRobots(actor, existing.getRobot().getId(), input.getRobot().getId());
        return mapper.toUpdate(this.update(input));
    }

    @Transactional
    public void deleteById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Task existing = lock(id);
        access.lockRobots(actor, existing.getRobot().getId());
        this.deleteById(id);
    }

    private Task lock(long id) {
        validation.requirePositiveId(id, "Task id");
        return taskRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Task prepareValues(Task input) {
        Task candidate = new Task();
        candidate.setName(input.getName());
        candidate.setStartAt(input.getStartAt());
        candidate.setDuration(input.getDuration());
        candidate.setStatus(input.getStatus());
        candidate.setRobot(input.getRobot());
        validation.validate(candidate);
        long robotId = candidate.getRobot().getId();
        validation.requirePositiveId(robotId, "Robot id");
        candidate.setRobot(requireRobot(robotId));
        return candidate;
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
