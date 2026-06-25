package com.aaelevator.qbintegration.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final String jwtSecret;
    private final int expirationHours;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration.hours}") int expirationHours) {
        this.jwtSecret = jwtSecret;
        this.expirationHours = expirationHours;
    }

    private SecretKey getSingingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String qbEmpresa) {
        return Jwts.builder()
                .subject(email)
                .claim("qbEmpresa", qbEmpresa)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationHours * 3600_000L))
                .signWith(getSingingKey())
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSingingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail (String token) {
        return validateToken(token).getSubject();
    }

    public String extractQbEmpresa (String token) {
        return validateToken(token).get("qbEmpresa",  String.class);
    }
}
