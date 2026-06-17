package com.aaelevator.qbintegration.repository;

import com.aaelevator.qbintegration.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface CustomerRepository extends JpaRepository<Customer, String> {
}
