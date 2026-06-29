package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    @Query("SELECT MAX(i.txnDate) FROM Invoice i")
    Optional<LocalDate> findMaxTxnDate();

    @Query("SELECT MAX(i.txnDate) FROM Invoice i WHERE i.txnDate < :cutoff")
    Optional<java.time.LocalDate> findMaxTxnDateBefore(@Param("cutoff") java.time.LocalDate cutoff);

}
