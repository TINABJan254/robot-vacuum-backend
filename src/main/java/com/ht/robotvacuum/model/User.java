package com.ht.robotvacuum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "user")
    private List<Robot> robots = new ArrayList<>();

}
