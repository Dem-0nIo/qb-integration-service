package com.aaelevator.qbintegration.config;

import com.aaelevator.qbintegration.service.AuditService;
import com.aaelevator.qbintegration.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuditService auditService;

    public JwtAuthenticationFilter(JwtService jwtService, AuditService auditService) {

        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = jwtService.validateToken(token);
                String email = claims.getSubject();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email,
                                                                    null,
                                                                    List.of( new SimpleGrantedAuthority("ROLE_CLIENT")));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Registrar acceso a endpoint protegido
                auditService.log("DATA_ACCESS", email, request, null);
            } catch (JwtException e) {
                logger.warn("Invalid JWT Token: "  + e.getMessage());
                auditService.log("JWT_INVALID", null, request, e.getMessage());

            }

            filterChain.doFilter(request, response);
        }
}
