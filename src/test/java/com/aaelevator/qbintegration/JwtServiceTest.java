package com.aaelevator.qbintegration.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de JwtService.
 *
 * Contexto de negocio: el claim "qbEmpresa" es el mecanismo que
 * ClientPortalController usa para segregar datos por cliente
 * (extractQbEmpresa(token), NUNCA SecurityContextHolder — ver nota
 * de la regresión de Boheme Condo). Estas pruebas no cubren el
 * controller, pero blindan que el token en sí siempre transporte
 * el claim correcto y que fallos de firma/expiración se detecten
 * como excepciones, no como datos corruptos silenciosos.
 */
class JwtServiceTest {

    // Secreto de prueba de 32+ bytes, requerido por HS256.
    private static final String TEST_SECRET =
            "test-secret-key-please-do-not-use-in-prod-0123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // expiración larga por defecto; los tests de expiración
        // instancian su propio JwtService con expirationHours negativo.
        jwtService = new JwtService(TEST_SECRET, 8);
    }

    @Test
    @DisplayName("generateToken + extractEmail devuelve el email original")
    void generateToken_thenExtractEmail_returnsOriginalEmail() {
        String token = jwtService.generateToken("cliente@boheme.com", "Boheme Condo");

        assertThat(jwtService.extractEmail(token)).isEqualTo("cliente@boheme.com");
    }

    @Test
    @DisplayName("generateToken + extractQbEmpresa devuelve la empresa QB original")
    void generateToken_thenExtractQbEmpresa_returnsOriginalCompany() {
        String token = jwtService.generateToken("cliente@boheme.com", "Boheme Condo");

        assertThat(jwtService.extractQbEmpresa(token)).isEqualTo("Boheme Condo");
    }

    @Test
    @DisplayName("el claim se llama exactamente 'qbEmpresa' (contrato usado por ClientPortalController)")
    void validateToken_claimKeyIsExactlyQbEmpresa() {
        String token = jwtService.generateToken("a@a.com", "A&A Elevator");
        Claims claims = jwtService.validateToken(token);

        assertThat(claims.get("qbEmpresa", String.class)).isEqualTo("A&A Elevator");
    }

    @Test
    @DisplayName("tokens de distintos clientes nunca deben cruzar su qbEmpresa (aislamiento por tenant)")
    void tokensForDifferentCompanies_neverCrossQbEmpresa() {
        String tokenA = jwtService.generateToken("clienteA@a.com", "Boheme Condo");
        String tokenB = jwtService.generateToken("clienteB@b.com", "A&A Elevator");

        assertThat(jwtService.extractQbEmpresa(tokenA)).isEqualTo("Boheme Condo");
        assertThat(jwtService.extractQbEmpresa(tokenB)).isEqualTo("A&A Elevator");
        assertThat(jwtService.extractQbEmpresa(tokenA))
                .isNotEqualTo(jwtService.extractQbEmpresa(tokenB));
    }

    @Test
    @DisplayName("un token expirado lanza ExpiredJwtException al validarlo")
    void expiredToken_throwsExpiredJwtException() {
        // expirationHours negativo garantiza una fecha de expiración en el pasado
        JwtService expiringNow = new JwtService(TEST_SECRET, -1);
        String token = expiringNow.generateToken("cliente@a.com", "A&A Elevator");

        assertThatThrownBy(() -> expiringNow.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("un token firmado con otro secreto lanza SignatureException")
    void tokenSignedWithDifferentSecret_throwsSignatureException() {
        JwtService otherService = new JwtService(
                "another-completely-different-secret-key-0123456789", 8);
        String tokenFromOther = otherService.generateToken("cliente@a.com", "A&A Elevator");

        assertThatThrownBy(() -> jwtService.validateToken(tokenFromOther))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("una cadena que no es un JWT válido lanza MalformedJwtException")
    void malformedToken_throwsMalformedJwtException() {
        assertThatThrownBy(() -> jwtService.validateToken("esto-no-es-un-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }
}
