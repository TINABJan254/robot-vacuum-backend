package com.ht.robotvacuum.service;

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
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }
}
