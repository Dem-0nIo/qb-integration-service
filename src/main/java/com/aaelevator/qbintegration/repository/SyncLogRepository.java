package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Optional<SyncLog> findTopByEntityTypeAndStatusOrderByCompletedAtDesc(
            String entityType, String status);

    Optional<SyncLog> findTopByEntityTypeAndStatusOrderByStartedAtDesc(
            String entityType, String status);

    List<SyncLog> findByStatusAndStartedAtBefore(
            String status, LocalDateTime cutoff);

}