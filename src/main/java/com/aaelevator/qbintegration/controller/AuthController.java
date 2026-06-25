package com.aaelevator.qbintegration.controller;


import com.aaelevator.qbintegration.service.OtpService;
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

    @PostMapping("/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String chanel = request.getOrDefault("channel", "email");
        otpService.requestOtp(email, chanel);
        return ResponseEntity.ok(Map.of("message", "If this email is registered, you will receive an access code shortly."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otpCode = request.get("otp");
        Optional<String> jwt = otpService.verifyOtp(email, otpCode);
        if (jwt.isPresent()) {
            return ResponseEntity.ok(Map.of("token", jwt.get()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired code."));
        }
    }
}
