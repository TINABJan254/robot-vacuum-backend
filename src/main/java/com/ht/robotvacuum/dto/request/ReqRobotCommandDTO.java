package com.ht.robotvacuum.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ht.robotvacuum.model.enums.RobotCommandAction;
import com.ht.robotvacuum.model.enums.RobotDirection;
import com.ht.robotvacuum.model.enums.RobotMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqRobotCommandDTO {

    @NotNull
    private RobotCommandAction action;

    private RobotMode mode;
    private RobotDirection direction;

    @JsonIgnore
    @AssertTrue(message = "Command parameters do not match the action")
    public boolean isParametersValid() {
        if (action == null) {
            return true; // Report the missing action using @NotNull.
        }
        return switch (action) {
            case START -> direction == null;
            case SET_MODE -> mode != null && direction == null;
            case MOVE -> direction != null && mode == null;
            case STOP, STOP_MOVEMENT, RETURN_TO_DOCK -> mode == null && direction == null;
        };
    }
}
