package com.aaelevator.qbintegration.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")

public class InvoiceLine {

    @Id
    @Column(name = "txn_line_id", nullable = false, unique = true)
    private String txnLineId;

    @ManyToOne
    @JoinColumn(name = "invoice_txn_id", nullable = false)
    private Invoice invoice;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "quantity", precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "rate", precision = 12, scale = 2)
    private BigDecimal rate;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    //Getters y setters
    public String getTxnLineId() {return txnLineId;}
    public void setTxnLineId(String txnLineId) {this.txnLineId = txnLineId;}

    public Invoice getInvoice() {return invoice;}
    public void setInvoice(Invoice invoice) {this.invoice = invoice;}

    public String getDescription() { return description;}
    public void setDescription(String description) {this.description = description;}

    public BigDecimal getQuantity() { return quantity;}
    public void setQuantity(BigDecimal quantity) {this.quantity = quantity;}

    public BigDecimal getRate() { return rate;}
    public void setRate(BigDecimal rate) {this.rate = rate;}

    public BigDecimal getAmount() { return amount;}
    public void setAmount(BigDecimal amount) {this.amount = amount;}

}
