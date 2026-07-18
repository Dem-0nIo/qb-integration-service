package com.aaelevator.qbintegration.controller;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Integer ticketId) {
        super("Ticket not found: " + ticketId);
    }
}
