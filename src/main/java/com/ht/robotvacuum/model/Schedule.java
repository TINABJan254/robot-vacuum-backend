package com.ht.robotvacuum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedule")
@Getter
@Setter
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @Column(nullable = false)
    private LocalTime time;

    @Column(nullable = false)
    private boolean enabled;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @OneToMany(mappedBy = "schedule")
    private List<RepeatSchedule> repeatSchedules = new ArrayList<>();
}
