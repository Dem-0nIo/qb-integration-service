package com.aaelevator.qbintegration.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InvoiceSummaryDTO {

    private String txnNumber;
    private LocalDate txnDate;
    private BigDecimal subtotal;
    private BigDecimal balanceRemaining;
    private boolean isPaid;

    public InvoiceSummaryDTO(String txnNumber, LocalDate txnDate,
                             BigDecimal subtotal, BigDecimal balanceRemaining,
                             boolean isPaid) {
        this.txnNumber = txnNumber;
        this.txnDate = txnDate;
        this.subtotal = subtotal;
        this.balanceRemaining = balanceRemaining;
        this.isPaid = isPaid;
    }

    public String getTxnNumber() {
        return txnNumber;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getBalanceRemaining() {
        return balanceRemaining;
    }

    public boolean isPaid() {
        return isPaid;
    }
}
