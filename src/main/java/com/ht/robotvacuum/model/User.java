package com.ht.robotvacuum.model;

import com.ht.robotvacuum.model.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;

    // Store a password hash supplied by the authentication service.
    @NotBlank
    @Column(nullable = false)
    private String password;

    // SHA-256 hash of the current refresh token; null when no token is active.
    @Column(name = "refresh_token", length = 64)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @OneToMany(mappedBy = "user")
    private List<Robot> robots = new ArrayList<>();

}
