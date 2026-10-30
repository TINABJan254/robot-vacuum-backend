package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResGetTaskDTO;
import com.ht.robotvacuum.model.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public ResGetTaskDTO toGet(Task value) {
        return new ResGetTaskDTO(value.getId(), value.getStartedAt(), value.getStatus(),
                value.getFinishedAt(), value.getRobot().getId());
    }
}
