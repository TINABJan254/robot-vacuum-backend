package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResGetRepeatScheduleDTO;
import com.ht.robotvacuum.model.RepeatSchedule;
import org.springframework.stereotype.Component;

@Component
public class RepeatScheduleMapper {

    public ResGetRepeatScheduleDTO toGet(RepeatSchedule repeat) {
        return new ResGetRepeatScheduleDTO(repeat.getId(), repeat.getSchedule().getId(), repeat.getDayOfWeek());
    }
}
