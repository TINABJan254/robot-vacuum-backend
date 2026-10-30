package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResGetScheduleDTO;
import com.ht.robotvacuum.model.RepeatSchedule;
import com.ht.robotvacuum.model.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class ScheduleMapper {

    private final RepeatScheduleMapper repeatMapper;

    public ResGetScheduleDTO toGet(Schedule schedule) {
        var days = schedule.getRepeatSchedules().stream()
                .sorted(Comparator.comparingInt(RepeatSchedule::getDayOfWeek))
                .map(repeatMapper::toGet).toList();
        return new ResGetScheduleDTO(schedule.getId(), schedule.getTime(), schedule.isEnabled(),
                schedule.getRobot().getId(), days);
    }
}
