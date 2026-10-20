package com.ht.robotvacuum.dto.response;

public record ResTokenDTO(String accessToken, String refreshToken, String tokenType,
                          long expiresIn, long refreshExpiresIn, ResAccountDTO user) {
    @Override
    public String toString() {
        return "ResTokenDTO[tokens=REDACTED]";
    }
}
