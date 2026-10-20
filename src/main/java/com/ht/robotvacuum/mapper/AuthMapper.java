package com.ht.robotvacuum.mapper;

import com.ht.robotvacuum.dto.response.ResAccountDTO;
import com.ht.robotvacuum.dto.response.ResRegisterDTO;
import com.ht.robotvacuum.model.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public ResAccountDTO toAccount(User user) {
        return new ResAccountDTO(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }

    public ResRegisterDTO toRegister(User user) {
        return new ResRegisterDTO(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }
}
