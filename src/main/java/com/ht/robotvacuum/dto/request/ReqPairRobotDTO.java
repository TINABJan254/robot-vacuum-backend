package com.ht.robotvacuum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqPairRobotDTO {

    @NotNull
    @Positive
    private Long robotId;

    @NotBlank
    @Size(max = 255)
    private String pairCode;
}
