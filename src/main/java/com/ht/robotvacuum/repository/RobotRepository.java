package com.ht.robotvacuum.repository;

import com.ht.robotvacuum.model.Robot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    Optional<Robot> findByPairCode(String pairCode);

    boolean existsByPairCode(String pairCode);

    boolean existsByPairCodeAndIdNot(String pairCode, long id);

    Page<Robot> findByUserId(long userId, Pageable pageable);

    boolean existsByUserId(long userId);
}
