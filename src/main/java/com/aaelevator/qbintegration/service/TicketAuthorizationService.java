package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.repository.CustomerMappingRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketAuthorizationService {

    private final CustomerMappingRepository customerMappingRepository;
    private final JdbcTemplate wordpressJdbcTemplate;

    public TicketAuthorizationService(CustomerMappingRepository customerMappingRepository,
                                      @Qualifier("wordPressJdbcTemplate") JdbcTemplate wordpressJdbcTemplate) {
        this.customerMappingRepository = customerMappingRepository;
        this.wordpressJdbcTemplate = wordpressJdbcTemplate;
    }
    /**
     * Verifica que el ticket pertenezca a la empresa QB del cliente autenticado.
     * Cadena: ticket -> building -> customer_id -> customer_mapping.qb_empresa
     */
    public boolean belongsToCompany(Integer ticketId, String qbEmpresa) {
        // Paso 1: customer_id del ticket (wordpress_db)
        Integer customerId;
        try {
            customerId = wordpressJdbcTemplate.queryForObject("""
                SELECT b.customer_id
                FROM ticketmain t
                JOIN building b ON t.Building_id = b.id
                WHERE t.id = ?""", Integer.class, ticketId);
        } catch (EmptyResultDataAccessException e) {
            return false; //el ticket no existe
        }

        if (customerId == null) {
            return false;
        }

        // Paso 2: ¿ese customer mapea a la empresa del token? (qb_integration_db)
        return customerMappingRepository.existsByQbEmpresaAndTicketCustomerId(qbEmpresa, customerId);
    }
}
