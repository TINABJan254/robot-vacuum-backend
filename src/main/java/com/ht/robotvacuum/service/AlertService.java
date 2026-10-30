package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.ResGetAlertDTO;
import com.ht.robotvacuum.dto.response.ResPageDTO;
import com.ht.robotvacuum.mapper.AlertMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        validation.validate(alert);
        if (alert.getMessage() != null) {
            Assert.isTrue(alert.getMessage().getBytes(StandardCharsets.UTF_8).length <= 65_535,
                    "Message exceeds TEXT capacity");
        }
        long robotId = alert.getRobot().getId();
        validation.requirePositiveId(robotId, "Robot id");
        Robot robot = requireRobot(robotId);

        Alert created = new Alert();
        created.setTitle(alert.getTitle());
        created.setMessage(alert.getMessage());
        created.setRobot(robot);
        return alertRepository.save(created);
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
        validation.validate(alert);
        if (alert.getMessage() != null) {
            Assert.isTrue(alert.getMessage().getBytes(StandardCharsets.UTF_8).length <= 65_535,
                    "Message exceeds TEXT capacity");
        }
        long robotId = alert.getRobot().getId();
        validation.requirePositiveId(robotId, "Robot id");
        Robot robot = requireRobot(robotId);

        existing.setTitle(alert.getTitle());
        existing.setMessage(alert.getMessage());
        existing.setReadAt(alert.getReadAt());
        existing.setRobot(robot);
        return alertRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        alertRepository.delete(findAlert(id));
    }

    // HTTP operations enforce authorization in the same transaction.
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

    public ResPageDTO<ResGetAlertDTO> getByRobotId(@Positive long robotId, @NotNull Pageable pageable,
                                                 Authentication authentication) {
        access.robot(access.actor(authentication), robotId);
        return ResPageDTO.from(alertRepository.findByRobotId(robotId, pageable).map(mapper::toGet));
    }

    @Transactional
    public ResGetAlertDTO update(@Positive long id, Instant readAt, Authentication authentication) {
        var actor = access.actor(authentication);
        Alert existing = lock(id);
        access.lockRobots(actor, existing.getRobot().getId());
        existing.setReadAt(readAt);
        return mapper.toGet(existing);
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
