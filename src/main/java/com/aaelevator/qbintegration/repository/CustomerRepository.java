package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    boolean existsByEmailAndIsActiveTrue(String email);
    Optional<Customer> findFirstByEmailAndIsActiveTrue(String email);
    List<Customer> findAllByIsActiveTrue();

}