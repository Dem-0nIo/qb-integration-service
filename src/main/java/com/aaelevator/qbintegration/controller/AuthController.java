package com.aaelevator.qbintegration.controller;


import com.aaelevator.qbintegration.service.AuditService;
import com.aaelevator.qbintegration.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private OtpService otpService;

    @Autowired
    private AuditService auditService;

    @PostMapping("/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = request.get("email");
        String channel = request.getOrDefault("channel", "email");
        otpService.requestOtp(email, channel);

        // Registrar solicitud de OTP independientemente de si el email existe
        // (la respuesta al cliente es siempre la misma para no revelar si el email está registrado)
        auditService.log("OTP_REQUESTED", email, httpRequest, "channel=" + channel);

        return ResponseEntity.ok(Map.of("message", "If this email is registered, you will receive an access code shortly."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = request.get("email");
        String otpCode = request.get("otp");
        Optional<String> jwt = otpService.verifyOtp(email, otpCode);
        if (jwt.isPresent()) {
            auditService.log("LOGIN_SUCCESS", email, httpRequest, "OTP verified successfully");
            return ResponseEntity.ok(Map.of("token", jwt.get()));
        } else {
            auditService.log("LOGIN_FAILURE", email, httpRequest, "Invalid or expired OTP code");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired code."));
        }
    }
}
