package com.ht.robotvacuum.repository;

import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.model.enums.TaskStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    boolean existsByRobotIdAndStatus(long robotId, TaskStatus status);

    boolean existsByRobotIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            long robotId, Instant from, Instant to);

    Page<Task> findByRobotUserId(long userId, Pageable pageable);

    Page<Task> findByRobotId(long robotId, Pageable pageable);

    boolean existsByRobotId(long robotId);
}
