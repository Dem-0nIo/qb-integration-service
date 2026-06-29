package com.aaelevator.qbintegration.service;


import com.aaelevator.qbintegration.entity.AuditLog;
import com.aaelevator.qbintegration.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log (String eventType, String email, HttpServletRequest request, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEventType(eventType);
            entry.setEmail(email);
            entry.setIpAdress(extractIp(request));
            entry.setEndpoint(request != null ? request.getRequestURI() : null);
            entry.setDetails(details);
            auditLogRepository.save(entry);
        } catch ( Exception e) {
            log.error("Failed to write audit log entry [{}] for email [{}]: {}", eventType, email, e.getMessage());
        }
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) { return null; }
        // Respeta headers de proxy/VPN (WatchGuard Firebox)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) { return forwarded.split(",")[0].trim(); }
        return request.getRemoteAddr();
    }
}
