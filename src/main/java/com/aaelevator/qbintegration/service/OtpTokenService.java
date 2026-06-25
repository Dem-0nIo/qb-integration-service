package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.OtpToken;
import com.aaelevator.qbintegration.repository.OtpTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OtpTokenService {

    private final OtpTokenRepository otpTokenRepository;

    public OtpTokenService(OtpTokenRepository otpTokenRepository) {
        this.otpTokenRepository = otpTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOtpToken(String email, String otpCode) {
        otpTokenRepository.deleteByEmailAndUsedTrue(email);
        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setOtpCode(otpCode);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        otpTokenRepository.save(token);
    }
}
