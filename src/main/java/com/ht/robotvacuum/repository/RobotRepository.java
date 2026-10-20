package com.ht.robotvacuum.repository;

import com.ht.robotvacuum.model.Robot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Robot r where r.id = :id")
    Optional<Robot> findLockedById(@Param("id") long id);

    Optional<Robot> findByPairCode(String pairCode);

    boolean existsByPairCode(String pairCode);

    boolean existsByPairCodeAndIdNot(String pairCode, long id);

    Page<Robot> findByUserId(long userId, Pageable pageable);

    boolean existsByUserId(long userId);
}
