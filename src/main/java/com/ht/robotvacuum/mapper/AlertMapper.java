package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResGetAlertDTO;
import com.ht.robotvacuum.model.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertMapper {

    private final RobotMapper robotMapper;

    public ResGetAlertDTO toGet(Alert value) {
        return new ResGetAlertDTO(value.getId(), value.getTitle(), value.getMessage(), value.getReadAt(),
                value.getCreatedAt(), robotMapper.toGet(value.getRobot()));
    }
}
