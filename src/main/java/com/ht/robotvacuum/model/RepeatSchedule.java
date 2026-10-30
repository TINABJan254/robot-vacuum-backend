package com.ht.robotvacuum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "repeat_schedule", uniqueConstraints = @UniqueConstraint(
        name = "uk_repeat_schedule_schedule_day", columnNames = {"schedule_id", "day_of_week"}))
@Getter
@Setter
public class RepeatSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // ISO day of week: 1 = Monday, 7 = Sunday.
    @Min(1)
    @Max(7)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;
}
