package com.aaelevator.qbintegration.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")

public class Invoice {

    @Id
    @Column(name = "txn_id", nullable = false, unique = true)
    private String txnId;

    @Column(name = "txn_number")
    private String txnNumber;

    @Column(name = "ref_number")
    private String refNumber;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name ="due_date")
    private LocalDate dueDate;

    @Column(name = "customer_list_id")
    private String customerListId;

    @Column(name = "customer_full_name")
    private String customerFullName;

    @Column(name = "subtotal", precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "balance_remaining", precision = 12, scale = 2)
    private BigDecimal balanceRemaining;

    @Column(name = "is_paid")
    private Boolean isPaid;

    @Column(name =  "synced_at")
    private LocalDateTime syncedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    //Getters y setters
    public String getTxnId() {return txnId;}
    public void setTxnId(String txnId) {this.txnId = txnId;}

    public String getTxnNumber() {return txnNumber;}
    public void setTxnNumber(String txnNumber) {this.txnNumber = txnNumber;}

    public String getRefNumber() {return refNumber;}
    public void setRefNumber(String refNumber) {this.refNumber = refNumber;}

    public LocalDate getTxnDate() {return txnDate;}
    public void setTxnDate(LocalDate txnDate) {this.txnDate = txnDate;}

    public LocalDate getDueDate() {return dueDate;}
    public void setDueDate(LocalDate dueDate) {this.dueDate = dueDate;}

    public String getCustomerListId() {return customerListId;}
    public void setCustomerListId(String customerListId) {this.customerListId = customerListId;}

    public String getCustomerFullName() {return customerFullName;}
    public void setCustomerFullName(String customerFullName) {this.customerFullName = customerFullName;}

    public BigDecimal getSubtotal() {return subtotal;}
    public void setSubtotal(BigDecimal subtotal) {this.subtotal = subtotal;}

    public BigDecimal getBalanceRemaining() {return balanceRemaining;}
    public void setBalanceRemaining(BigDecimal balanceRemaining) {this.balanceRemaining = balanceRemaining;}

    public Boolean getIsPaid() {return isPaid;}
    public void setIsPaid(Boolean isPaid) {this.isPaid = isPaid;}

    public LocalDateTime getSyncedAt() {return syncedAt;}
    public void setSyncedAt(LocalDateTime syncedAt) {this.syncedAt = syncedAt;}

    public List<InvoiceLine> getLines() {return lines;}
    public void setLines(List<InvoiceLine> lines) {this.lines = lines;}



}
