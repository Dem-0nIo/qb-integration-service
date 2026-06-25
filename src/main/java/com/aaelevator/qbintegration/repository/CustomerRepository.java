package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    boolean existsByEmail(String email);
    Optional<Customer> findFirstByEmail(String email);

}
