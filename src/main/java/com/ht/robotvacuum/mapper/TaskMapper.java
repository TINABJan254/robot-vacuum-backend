package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResCreateTaskDTO;
import com.ht.robotvacuum.dto.response.ResUpdateTaskDTO;
import com.ht.robotvacuum.dto.response.ResGetTaskDTO;
import com.ht.robotvacuum.model.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public ResCreateTaskDTO toCreate(Task value) {
        return new ResCreateTaskDTO(value.getId(), value.getName(), value.getStartAt(), value.getDuration(), value.getStatus(), value.getCreatedAt(), value.getRobot().getId());
    }

    public ResUpdateTaskDTO toUpdate(Task value) {
        return new ResUpdateTaskDTO(value.getId(), value.getName(), value.getStartAt(), value.getDuration(), value.getStatus(), value.getCreatedAt(), value.getRobot().getId());
    }

    public ResGetTaskDTO toGet(Task value) {
        return new ResGetTaskDTO(value.getId(), value.getName(), value.getStartAt(), value.getDuration(), value.getStatus(), value.getCreatedAt(), value.getRobot().getId());
    }
}
