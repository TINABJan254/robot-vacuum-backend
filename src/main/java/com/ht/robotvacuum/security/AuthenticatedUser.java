package com.ht.robotvacuum.security;

import com.ht.robotvacuum.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final long userId;

    public AuthenticatedUser(User user) {
        super(user.getUsername(), user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.userId = user.getId();
    }

    public long getUserId() {
        return userId;
    }
}
