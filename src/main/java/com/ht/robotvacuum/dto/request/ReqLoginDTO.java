package com.ht.robotvacuum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReqLoginDTO(@NotBlank @Size(max = 255) String username,
                          @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
        return "ReqLoginDTO[credentials=REDACTED]";
    }
}
