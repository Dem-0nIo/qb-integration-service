package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.service.JwtService;
import com.aaelevator.qbintegration.service.TicketAuthorizationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/portal/tickets")
@Validated
public class TicketPdfController {

    private final JwtService jwtService;
    private final TicketAuthorizationService ticketAuthorizationService;
    private final RestClient wpPdfRestClient;

    public TicketPdfController(JwtService jwtService,
                               TicketAuthorizationService ticketAuthorizationService,
                               RestClient wpPdfRestClient) {
        this.jwtService = jwtService;
        this.ticketAuthorizationService = ticketAuthorizationService;
        this.wpPdfRestClient = wpPdfRestClient;
    }

    @GetMapping("/{ticketId}/pdf")
    public ResponseEntity<byte[]> getTicketPdf(
            @PathVariable @Positive Integer ticketId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        String qbEmpresa = jwtService.extractQbEmpresa(authHeader.substring(7));

        if (!ticketAuthorizationService.belongsToCompany(ticketId, qbEmpresa)) {
            // 404 (no 403): no revelar existencia de tickets de otros clientes
            throw new TicketNotFoundException(ticketId);
        }

        byte[] pdf = wpPdfRestClient.get()
                .uri("/wp-json/aae/v1/ticket-pdf/{id}", ticketId)
                .retrieve()
                .body(byte[].class);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"service-summary-" + ticketId + ".pdf\"")
                .body(pdf);
    }
}