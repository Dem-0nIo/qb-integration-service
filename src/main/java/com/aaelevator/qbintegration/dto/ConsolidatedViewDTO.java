package com.aaelevator.qbintegration.dto;

import java.util.List;

public class ConsolidatedViewDTO {

    private String periodo; // formato YYYY-MM
    private List<InvoiceSummaryDTO> facturas;
    private List<TicketSummaryDTO> tickets;

    public ConsolidatedViewDTO(String periodo, List<InvoiceSummaryDTO> facturas,
                               List<TicketSummaryDTO> tickets) {
        this.periodo = periodo;
        this.facturas = facturas;
        this.tickets = tickets;
    }

    public String getPeriodo() {
        return periodo;
    }

    public List<InvoiceSummaryDTO> getFacturas() {
        return facturas;
    }

    public List<TicketSummaryDTO> getTickets() {
        return tickets;
    }
}