package com.ht.robotvacuum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReqRefreshTokenDTO(@NotBlank @Size(max = 512) String refreshToken) {
    @Override
    public String toString() {
        return "ReqRefreshTokenDTO[token=REDACTED]";
    }
}
