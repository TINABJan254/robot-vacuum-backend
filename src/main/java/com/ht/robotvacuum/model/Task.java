package com.ht.robotvacuum.model;

import com.ht.robotvacuum.model.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

}
