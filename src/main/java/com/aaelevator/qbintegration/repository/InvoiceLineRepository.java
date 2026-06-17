package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, String> {
}
