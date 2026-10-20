package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.mapper.AlertMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.util.Assert;
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
    private final CrudAccess access;
    private final AlertMapper mapper;

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

    // These overloads keep authorization, persistence and DTO mapping in one transaction.
    @Transactional
    public ResCreateAlertDTO create(@NotNull Alert input, Authentication authentication) {
        var actor = access.actor(authentication);
        validation.validate(input);
        access.lockRobots(actor, input.getRobot().getId());
        return mapper.toCreate(this.create(input));
    }

    public ResGetAlertDTO getById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Alert existing = this.getById(id);
        access.ownsRobot(actor, existing.getRobot());
        return mapper.toGet(existing);
    }

    public ResPageDTO<ResGetAlertDTO> getAll(@NotNull Pageable pageable, Authentication authentication) {
        var actor = access.actor(authentication);
        var page = actor.admin() ? this.getAll(pageable) : alertRepository.findByRobotUserId(actor.id(), pageable);
        return ResPageDTO.from(page.map(mapper::toGet));
    }

    @Transactional
    public ResUpdateAlertDTO update(@NotNull Alert input, Authentication authentication) {
        var actor = access.actor(authentication);
        Alert existing = lock(input.getId());
        validation.validate(input);
        access.lockRobots(actor, existing.getRobot().getId(), input.getRobot().getId());
        return mapper.toUpdate(this.update(input));
    }

    @Transactional
    public void deleteById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Alert existing = lock(id);
        access.lockRobots(actor, existing.getRobot().getId());
        this.deleteById(id);
    }

    private Alert lock(long id) {
        validation.requirePositiveId(id, "Alert id");
        return alertRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }

    private Alert prepareValues(Alert input) {
        Alert candidate = new Alert();
        candidate.setType(input.getType());
        candidate.setMessage(input.getMessage());
        candidate.setRobot(input.getRobot());
        validation.validate(candidate);
        Assert.isTrue(candidate.getMessage().getBytes(StandardCharsets.UTF_8).length <= 65_535,
                "Message exceeds TEXT capacity");
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
        validation.requirePositiveId(id, "Alert id");
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }
}
