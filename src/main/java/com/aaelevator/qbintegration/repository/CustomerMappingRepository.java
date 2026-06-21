package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.CustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerMappingRepository extends JpaRepository<CustomerMapping, Long> {

    List<CustomerMapping> findByQbEmpresa (String qbEmpresa);

    boolean existsByQbEmpresaAndTicketCustomerId (String qbEmpresa, Integer ticketCustomerId);

}
