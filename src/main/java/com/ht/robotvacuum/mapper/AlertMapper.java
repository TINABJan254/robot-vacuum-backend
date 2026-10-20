package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResCreateAlertDTO;
import com.ht.robotvacuum.dto.response.ResUpdateAlertDTO;
import com.ht.robotvacuum.dto.response.ResGetAlertDTO;
import com.ht.robotvacuum.model.Alert;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    public ResCreateAlertDTO toCreate(Alert value) {
        return new ResCreateAlertDTO(value.getId(), value.getType(), value.getMessage(), value.getReadAt(), value.getCreatedAt(), value.getRobot().getId());
    }

    public ResUpdateAlertDTO toUpdate(Alert value) {
        return new ResUpdateAlertDTO(value.getId(), value.getType(), value.getMessage(), value.getReadAt(), value.getCreatedAt(), value.getRobot().getId());
    }

    public ResGetAlertDTO toGet(Alert value) {
        return new ResGetAlertDTO(value.getId(), value.getType(), value.getMessage(), value.getReadAt(), value.getCreatedAt(), value.getRobot().getId());
    }
}
