package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResCreateUserDTO;
import com.ht.robotvacuum.dto.response.ResUpdateUserDTO;
import com.ht.robotvacuum.dto.response.ResGetUserDTO;
import com.ht.robotvacuum.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public ResCreateUserDTO toCreate(User value) {
        return new ResCreateUserDTO(value.getId(), value.getName(), value.getUsername(), value.getRole());
    }

    public ResUpdateUserDTO toUpdate(User value) {
        return new ResUpdateUserDTO(value.getId(), value.getName(), value.getUsername(), value.getRole());
    }

    public ResGetUserDTO toGet(User value) {
        return new ResGetUserDTO(value.getId(), value.getName(), value.getUsername(), value.getRole());
    }
}
