package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.dto.ConsolidatedViewDTO;
import com.aaelevator.qbintegration.service.ConsolidatedViewService;
import com.aaelevator.qbintegration.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/client")
public class ClientPortalController {

    @Autowired
    private ConsolidatedViewService consolidatedViewService;

    @Autowired
    private JwtService jwtService;

    @GetMapping("/consolidated-view")
    /*public List<ConsolidatedViewDTO> getConsolidatedView(
            @RequestParam String qbEmpresa,
            @RequestParam(defaultValue = "12") int months) {
        return consolidatedViewService.getMaintenanceView(qbEmpresa, months);
    }*/
    public ResponseEntity<List<ConsolidatedViewDTO>> getConsolidatedView(@RequestParam (defaultValue = "12") @Min(1) @Max(120) int months, HttpServletRequest request) {

        // Extraer qbEmpresa del JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring("Bearer ".length()); // 7
        String qbEmpresa = jwtService.extractQbEmpresa(token);
        /*String qbEmpresa = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();*/

        if (qbEmpresa == null || qbEmpresa.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ConsolidatedViewDTO> result = consolidatedViewService.getMaintenanceView(qbEmpresa, months);
        return ResponseEntity.ok(result);
    }
}
