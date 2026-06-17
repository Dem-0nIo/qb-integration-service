package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {


}
