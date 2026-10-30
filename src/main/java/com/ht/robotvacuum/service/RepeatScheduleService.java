package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.ResGetRepeatScheduleDTO;
import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.mapper.RepeatScheduleMapper;
import com.ht.robotvacuum.model.RepeatSchedule;
import com.ht.robotvacuum.model.Schedule;
import com.ht.robotvacuum.repository.RepeatScheduleRepository;
import com.ht.robotvacuum.repository.ScheduleRepository;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RepeatScheduleService {

    private final RepeatScheduleRepository repeatRepository;
    private final ScheduleRepository scheduleRepository;
    private final CrudAccess access;
    private final ServiceValidation validation;
    private final RepeatScheduleMapper mapper;

    public ResGetRepeatScheduleDTO getById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        RepeatSchedule repeat = repeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RepeatSchedule", id));
        access.ownsRobot(actor, repeat.getSchedule().getRobot());
        return mapper.toGet(repeat);
    }

    public List<ResGetRepeatScheduleDTO> getByScheduleId(@Positive long scheduleId,
                                                       Authentication authentication) {
        var actor = access.actor(authentication);
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
        access.ownsRobot(actor, schedule.getRobot());
        return repeatRepository.findByScheduleIdOrderByDayOfWeekAsc(scheduleId).stream()
                .map(mapper::toGet).toList();
    }

    // Internal write: ScheduleService owns the transaction, parent lock and authorization.
    @Transactional(propagation = Propagation.MANDATORY)
    List<RepeatSchedule> replaceDays(Schedule schedule, List<Integer> daysOfWeek) {
        Assert.notNull(schedule, "Schedule is required");
        validation.requirePositiveId(schedule.getId(), "Schedule id");
        Assert.notNull(daysOfWeek, "Days of week are required");
        var selected = new HashSet<Integer>();
        for (Integer day : daysOfWeek) {
            Assert.isTrue(day != null && day >= 1 && day <= 7, "Day of week must be between 1 and 7");
            if (!selected.add(day)) {
                throw new DuplicateResourceException("A schedule cannot repeat the same day twice");
            }
        }

        var existing = repeatRepository.findByScheduleIdOrderByDayOfWeekAsc(schedule.getId());
        var removed = existing.stream().filter(repeat -> !selected.contains(repeat.getDayOfWeek())).toList();
        repeatRepository.deleteAll(removed);
        var result = new ArrayList<RepeatSchedule>();
        for (RepeatSchedule repeat : existing) {
            if (selected.remove(repeat.getDayOfWeek())) {
                result.add(repeat);
            }
        }
        var added = new ArrayList<RepeatSchedule>();
        for (Integer day : selected) {
            RepeatSchedule repeat = new RepeatSchedule();
            repeat.setSchedule(schedule);
            repeat.setDayOfWeek(day);
            added.add(repeat);
        }
        result.addAll(repeatRepository.saveAll(added));
        result.sort(Comparator.comparingInt(RepeatSchedule::getDayOfWeek));
        schedule.getRepeatSchedules().clear();
        schedule.getRepeatSchedules().addAll(result);
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void deleteByScheduleId(long scheduleId) {
        validation.requirePositiveId(scheduleId, "Schedule id");
        repeatRepository.deleteByScheduleId(scheduleId);
    }
}
