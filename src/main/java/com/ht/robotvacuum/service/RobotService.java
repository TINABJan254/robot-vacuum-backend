package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.mapper.RobotMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceInUseException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import com.ht.robotvacuum.repository.UserRepository;
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
public class RobotService {

    private final RobotRepository robotRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AlertRepository alertRepository;
    private final ServiceValidation validation;
    private final CrudAccess access;
    private final RobotMapper mapper;

    @Transactional
    public Robot create(@NotNull Robot robot) {
        validation.requireNewId(robot.getId());
        Robot created = prepareValues(robot);
        if (robotRepository.existsByPairCode(created.getPairCode())) {
            throw new DuplicateResourceException("Pair code is already in use");
        }
        return robotRepository.save(created);
    }

    public Robot getById(@Positive long id) {
        return findRobot(id);
    }

    public Page<Robot> getAll(@NotNull Pageable pageable) {
        return robotRepository.findAll(pageable);
    }

    public Page<Robot> getByUserId(@Positive long userId, @NotNull Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return robotRepository.findByUserId(userId, pageable);
    }

    @Transactional
    public Robot update(@NotNull Robot robot) {
        validation.requirePositiveId(robot.getId(), "Robot id");
        Robot existing = findRobot(robot.getId());
        Robot candidate = prepareValues(robot);
        if (robotRepository.existsByPairCodeAndIdNot(candidate.getPairCode(), existing.getId())) {
            throw new DuplicateResourceException("Pair code is already in use");
        }
        existing.setName(candidate.getName());
        existing.setPairCode(candidate.getPairCode());
        existing.setUser(candidate.getUser());
        return robotRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        Robot robot = findRobot(id);
        if (taskRepository.existsByRobotId(id) || alertRepository.existsByRobotId(id)) {
            throw new ResourceInUseException("Cannot delete a robot that still has tasks or alerts");
        }
        robotRepository.delete(robot);
    }

    // These overloads keep authorization, persistence and DTO mapping in one transaction.
    @Transactional
    public ResCreateRobotDTO create(@NotNull Robot input, Authentication authentication) {
        var actor = access.actor(authentication);
        validation.validate(input);
        access.user(actor, input.getUser().getId());
        return mapper.toCreate(this.create(input));
    }

    public ResGetRobotDTO getById(@Positive long id, Authentication authentication) {
        return mapper.toGet(access.robot(access.actor(authentication), id));
    }

    public ResPageDTO<ResGetRobotDTO> getAll(@NotNull Pageable pageable, Authentication authentication) {
        var actor = access.actor(authentication);
        var page = actor.admin() ? this.getAll(pageable) : this.getByUserId(actor.id(), pageable);
        return ResPageDTO.from(page.map(mapper::toGet));
    }

    @Transactional
    public ResUpdateRobotDTO update(@NotNull Robot input, Authentication authentication) {
        var actor = access.actor(authentication);
        access.lockRobots(actor, input.getId());
        validation.validate(input);
        access.user(actor, input.getUser().getId());
        return mapper.toUpdate(this.update(input));
    }

    @Transactional
    public void deleteById(@Positive long id, Authentication authentication) {
        access.lockRobots(access.actor(authentication), id);
        this.deleteById(id);
    }

    private Robot prepareValues(Robot input) {
        Robot candidate = new Robot();
        candidate.setName(input.getName());
        candidate.setPairCode(input.getPairCode());
        candidate.setUser(input.getUser());
        validation.validate(candidate);
        long userId = candidate.getUser().getId();
        validation.requirePositiveId(userId, "User id");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        candidate.setUser(user);
        return candidate;
    }

    private Robot findRobot(long id) {
        validation.requirePositiveId(id, "Robot id");
        return robotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Robot", id));
    }
}
