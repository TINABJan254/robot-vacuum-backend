package com.ht.robotvacuum.repository;

import com.ht.robotvacuum.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByRobotId(long robotId, Pageable pageable);

    boolean existsByRobotId(long robotId);
}
