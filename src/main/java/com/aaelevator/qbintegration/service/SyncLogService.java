package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.SyncLog;
import com.aaelevator.qbintegration.repository.SyncLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SyncLogService {

    private static final Logger log = LoggerFactory.getLogger(SyncLogService.class);

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSyncLog(String entityType, int recordCount) {
        syncLogRepository
                .findTopByEntityTypeAndStatusOrderByStartedAtDesc(entityType, "IN_PROGRESS")
                .ifPresent(syncLog -> {
                    syncLog.setStatus("SUCCESS");
                    syncLog.setRecordsProcessed(recordCount);
                    syncLog.setCompletedAt(LocalDateTime.now());
                    syncLogRepository.save(syncLog);
                });
    }

    @Transactional
    public int markStaleAsAbandoned(int hoursThreshold) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursThreshold);
        List<SyncLog> staleLogs = syncLogRepository
                .findByStatusAndStartedAtBefore("IN_PROGRESS", cutoff)
                .stream()
                .filter(sl -> !sl.getEntityType().equals("INVOICE_HISTORICAL"))
                .collect(java.util.stream.Collectors.toList());

        for (SyncLog log : staleLogs) {
            log.setStatus("ABANDONED");
            log.setErrorMessage("Session interrupted - service or QuickBooks was closed before sync completed");
        }
        syncLogRepository.saveAll(staleLogs);
        return staleLogs.size();
    }

    @Scheduled(cron = "0 0 * * * *") // cada hora, en el minuto 0
    public void scheduledCleanupOfStaleSyncLogs() {
        int count = markStaleAsAbandoned(1);
        if (count > 0) {
            log.info("Scheduled cleanup: marked {} stale sync_log records as ABANDONED", count);
        }
    }
}
