package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Optional<SyncLog> findTopByEntityTypeAndStatusOrderByStartedAtDesc(
            String entityType, String status);

    Optional<SyncLog> findTopByEntityTypeAndStatusOrderByCompletedAtDesc(
            String entityType, String status);

    // Nuevo — para alternar entre CUSTOMER e INVOICE basado en el último exitoso
    Optional<SyncLog> findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
            List<String> entityTypes, String status);

    List<SyncLog> findByStatusAndStartedAtBefore(String status, LocalDateTime before);
}