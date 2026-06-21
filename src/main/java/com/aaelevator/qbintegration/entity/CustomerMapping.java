package com.aaelevator.qbintegration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"qb_empresa", "ticket_customer_id"}))

public class CustomerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qb_empresa", nullable = false)
    private String qbEmpresa;

    @Column(name ="ticket_customer_id", nullable = false)
    private Integer ticketCustomerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name= "notes")
    private String notes;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQbEmpresa() { return qbEmpresa; }
    public void setQbEmpresa(String qbEmpresa) {
        this.qbEmpresa = qbEmpresa;
    }

    public Integer getTicketCustomerId() { return ticketCustomerId; }
    public void setTicketCustomerId(Integer ticketCustomerId) { this.ticketCustomerId = ticketCustomerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
