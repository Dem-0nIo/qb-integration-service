package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.service.SyncLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    @Qualifier("wordPressJdbcTemplate")
    private JdbcTemplate wordPressJdbcTemplate;

    @GetMapping("/api/admin/wordpress/test")
    public String testWordPressConnection() {
        try {
            Integer count = wordPressJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM customer WHERE active = 'Y'",
                    Integer.class);
            return "WordPress DB connected. Active customers: " + count;
        } catch (Exception e) {
            return "WordPress DB connection failed: " + e.getMessage();
        }
    }
}
