package com.ht.robotvacuum.dto.response;

import com.ht.robotvacuum.model.enums.Role;

public record ResCreateUserDTO(long id, String name, String username, Role role) {
}
