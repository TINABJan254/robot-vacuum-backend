package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResCreateRobotDTO;
import com.ht.robotvacuum.dto.response.ResUpdateRobotDTO;
import com.ht.robotvacuum.dto.response.ResGetRobotDTO;
import com.ht.robotvacuum.model.Robot;
import org.springframework.stereotype.Component;

@Component
public class RobotMapper {

    public ResCreateRobotDTO toCreate(Robot value) {
        return new ResCreateRobotDTO(value.getId(), value.getName(), value.getPairCode(), value.getUser().getId());
    }

    public ResUpdateRobotDTO toUpdate(Robot value) {
        return new ResUpdateRobotDTO(value.getId(), value.getName(), value.getPairCode(), value.getUser().getId());
    }

    public ResGetRobotDTO toGet(Robot value) {
        return new ResGetRobotDTO(value.getId(), value.getName(), value.getPairCode(), value.getUser().getId());
    }
}
