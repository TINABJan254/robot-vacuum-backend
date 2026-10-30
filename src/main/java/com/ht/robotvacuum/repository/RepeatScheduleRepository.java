package com.ht.robotvacuum.repository;

import com.ht.robotvacuum.model.RepeatSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepeatScheduleRepository extends JpaRepository<RepeatSchedule, Long> {

    List<RepeatSchedule> findByScheduleIdOrderByDayOfWeekAsc(long scheduleId);

    void deleteByScheduleId(long scheduleId);
}
