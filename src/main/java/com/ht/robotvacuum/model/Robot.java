package com.ht.robotvacuum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "robots")
@Getter
@Setter
public class Robot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, name = "pair_code", unique = true)
    private String pairCode;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "robot")
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "robot")
    private List<Alert> alerts = new ArrayList<>();

}
