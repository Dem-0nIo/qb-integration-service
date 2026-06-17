package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.service.SyncLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    @Autowired
    private SyncLogService syncLogService;

    @GetMapping("/api/admin/sync-log/clean-stale")
    public String cleanStaleSyncLogs(@RequestParam(defaultValue = "1") int hours) {
        int count = syncLogService.markStaleAsAbandoned(hours);
        return "Marked " + count + " stale sync_log records as ABANDONED";
    }
}
