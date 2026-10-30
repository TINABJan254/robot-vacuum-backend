package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.ResGetScheduleDTO;
import com.ht.robotvacuum.dto.response.ResPageDTO;
import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.mapper.ScheduleMapper;
import com.ht.robotvacuum.model.Schedule;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.ScheduleRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final RobotRepository robotRepository;
    private final RepeatScheduleService repeatService;
    private final CrudAccess access;
    private final ServiceValidation validation;
    private final ScheduleMapper mapper;

    @Transactional
    public ResGetScheduleDTO create(@Positive long robotId, @NotNull LocalTime time, boolean enabled,
                                    @NotNull List<Integer> daysOfWeek, Authentication authentication) {
        var actor = access.actor(authentication);
        Assert.notNull(time, "Schedule time is required");
        Assert.notNull(daysOfWeek, "Days of week are required");
        var selected = new HashSet<Integer>();
        for (Integer day : daysOfWeek) {
            Assert.isTrue(day != null && day >= 1 && day <= 7, "Day of week must be between 1 and 7");
            if (!selected.add(day)) {
                throw new DuplicateResourceException("A schedule cannot repeat the same day twice");
            }
        }
        access.lockRobots(actor, robotId);
        var robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ResourceNotFoundException("Robot", robotId));
        Schedule schedule = new Schedule();
        schedule.setTime(time);
        schedule.setEnabled(enabled);
        schedule.setRobot(robot);
        validation.validate(schedule);
        schedule = scheduleRepository.save(schedule);
        repeatService.replaceDays(schedule, daysOfWeek);
        return mapper.toGet(schedule);
    }

    @Transactional
    public ResGetScheduleDTO update(@Positive long id, LocalTime time, Boolean enabled,
                                    List<Integer> daysOfWeek, Authentication authentication) {
        var actor = access.actor(authentication);
        Schedule schedule = lock(id);
        access.lockRobots(actor, schedule.getRobot().getId());
        Assert.isTrue(time != null || enabled != null || daysOfWeek != null,
                "At least one schedule field is required");
        if (daysOfWeek != null) {
            var selected = new HashSet<Integer>();
            for (Integer day : daysOfWeek) {
                Assert.isTrue(day != null && day >= 1 && day <= 7, "Day of week must be between 1 and 7");
                if (!selected.add(day)) {
                    throw new DuplicateResourceException("A schedule cannot repeat the same day twice");
                }
            }
        }
        if (time != null) {
            schedule.setTime(time);
        }
        if (enabled != null) {
            schedule.setEnabled(enabled);
        }
        if (daysOfWeek != null) {
            repeatService.replaceDays(schedule, daysOfWeek);
        }
        return mapper.toGet(schedule);
    }

    public ResGetScheduleDTO getById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
        access.ownsRobot(actor, schedule.getRobot());
        return mapper.toGet(schedule);
    }

    public ResPageDTO<ResGetScheduleDTO> getAll(@NotNull Pageable pageable, Authentication authentication) {
        var actor = access.actor(authentication);
        var page = actor.admin() ? scheduleRepository.findAll(pageable)
                : scheduleRepository.findByRobotUserId(actor.id(), pageable);
        return ResPageDTO.from(page.map(mapper::toGet));
    }

    public ResPageDTO<ResGetScheduleDTO> getByRobotId(@Positive long robotId, @NotNull Pageable pageable,
                                                    Authentication authentication) {
        access.robot(access.actor(authentication), robotId);
        return ResPageDTO.from(scheduleRepository.findByRobotId(robotId, pageable).map(mapper::toGet));
    }

    @Transactional
    public void deleteById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        Schedule schedule = lock(id);
        access.lockRobots(actor, schedule.getRobot().getId());
        repeatService.deleteByScheduleId(id);
        scheduleRepository.delete(schedule);
    }

    private Schedule lock(long id) {
        validation.requirePositiveId(id, "Schedule id");
        return scheduleRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }
}
