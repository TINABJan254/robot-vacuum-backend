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
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, name = "pair_code", unique = true)
    private String pairCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; 

    @OneToMany(mappedBy = "robot")
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "robot")
    private List<Alert> alerts = new ArrayList<>();

    @OneToMany(mappedBy = "robot")
    private List<Schedule> schedules = new ArrayList<>();

}
