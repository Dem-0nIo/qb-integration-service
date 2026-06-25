package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Invoice;
import com.aaelevator.qbintegration.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    void deleteByEmailAndUsedTrue(String email);
}
