package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResGetAlertDTO;
import com.ht.robotvacuum.model.Alert;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    public ResGetAlertDTO toGet(Alert value) {
        return new ResGetAlertDTO(value.getId(), value.getTitle(), value.getMessage(), value.getReadAt(), value.getCreatedAt(), value.getRobot().getId());
    }
}
