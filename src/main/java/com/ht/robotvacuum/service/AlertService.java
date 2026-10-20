package com.ht.robotvacuum.service;

import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
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
public class AlertService {

    private final AlertRepository alertRepository;
    private final RobotRepository robotRepository;
    private final ServiceValidation validation;

    @Transactional
    public Alert create(@NotNull Alert alert) {
        validation.requireNewId(alert.getId());
        return alertRepository.save(prepareValues(alert));
    }

    public Alert getById(@Positive long id) {
        return findAlert(id);
    }

    public Page<Alert> getAll(@NotNull Pageable pageable) {
        return alertRepository.findAll(pageable);
    }

    public Page<Alert> getByRobotId(@Positive long robotId, @NotNull Pageable pageable) {
        requireRobot(robotId);
        return alertRepository.findByRobotId(robotId, pageable);
    }

    @Transactional
    public Alert update(@NotNull Alert alert) {
        validation.requirePositiveId(alert.getId(), "Alert id");
        Alert existing = findAlert(alert.getId());
        Alert candidate = prepareValues(alert);
        existing.setType(candidate.getType());
        existing.setMessage(candidate.getMessage());
        existing.setReadAt(alert.getReadAt());
        existing.setRobot(candidate.getRobot());
        return alertRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        alertRepository.delete(findAlert(id));
    }

    private Alert prepareValues(Alert input) {
        Alert candidate = new Alert();
        candidate.setType(input.getType());
        candidate.setMessage(input.getMessage());
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

    private Alert findAlert(long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }
}
