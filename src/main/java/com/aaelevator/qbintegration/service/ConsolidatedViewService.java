package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.dto.ConsolidatedViewDTO;
import com.aaelevator.qbintegration.dto.InvoiceSummaryDTO;
import com.aaelevator.qbintegration.dto.TicketSummaryDTO;
import com.aaelevator.qbintegration.entity.CustomerMapping;
import com.aaelevator.qbintegration.entity.Invoice;
import com.aaelevator.qbintegration.repository.CustomerMappingRepository;
import com.aaelevator.qbintegration.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsolidatedViewService {

    @Autowired
    private CustomerMappingRepository customerMappingRepository;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("wordPressJdbcTemplate")
    private JdbcTemplate wordPressJdbcTemplate;

    public List<ConsolidatedViewDTO> getMaintenanceView(String qbEmpresa, int months){

        // 1. Obtener todos los ticket_customer_id mapeados para esta empresa
        List<CustomerMapping> mappings = customerMappingRepository.findByQbEmpresa(qbEmpresa);
        if(mappings == null || mappings.isEmpty()){
            return Collections.emptyList();
        }

        List<Integer> ticketCustomerIds = mappings.stream()
                                        .map(CustomerMapping::getTicketCustomerId)
                                        .collect(Collectors.toList());

        LocalDate fromDate = LocalDate.now().minusMonths(months);
        DateTimeFormatter periodFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // 2. Traer facturas de qb_integration_db via JPA
        String invoiceSql = "SELECT i.txn_number, i.txn_date, i.subtotal, " +
                "i.balance_remaining, i.is_paid " +
                "FROM invoices i " +
                "JOIN customers c ON i.customer_list_id = c.list_id " +
                "WHERE COALESCE(NULLIF(c.company_name, ''), SUBSTRING_INDEX(c.full_name, ':', 1)) = ? " +
                "AND i.txn_date >= ? " +
                "ORDER BY i.txn_date DESC";

        Map<String, List<InvoiceSummaryDTO>> invoicesByPeriod = new LinkedHashMap<>();
        primaryJdbcTemplate.query(invoiceSql, rs -> {
            String periodo = rs.getObject("txn_date", LocalDate.class)
                    .format(periodFormatter);
            InvoiceSummaryDTO dto = new InvoiceSummaryDTO(
                    rs.getString("txn_number"),
                    rs.getObject("txn_date", LocalDate.class),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("balance_remaining"),
                    rs.getBoolean("is_paid")
            );
            invoicesByPeriod.computeIfAbsent(periodo, k -> new ArrayList<>()).add(dto);
        }, qbEmpresa, fromDate);

        // 3. Traer tickets de mantenimiento de wordpress_db via JdbcTemplate
        String inClause = ticketCustomerIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        String ticketSql = "SELECT " +
                "DATE_FORMAT(t.dateCallIn, '%Y-%m') AS periodo, " +
                "t.id AS ticket_id, " +
                "t.dateCallIn, " +
                "t.descriptionRequest, " +
                "t.descriptionWork, " +
                "t.workComplete, " +
                "tt.name AS ticket_type, " +
                "b.name AS building_name " +
                "FROM ticketmain t " +
                "JOIN building b ON b.id = t.Building_id " +
                "JOIN tickettype tt ON tt.id = t.tickettype_id " +
                "WHERE b.customer_id IN (" + inClause + ") " +
                "AND t.tickettype_id = 1 " +
                "AND t.active = 'Y' " +
                "AND t.dateCallIn >= ? " +
                "ORDER BY t.dateCallIn DESC";

        Map<String, List<TicketSummaryDTO>> ticketsByPeriod = new LinkedHashMap<>();

        wordPressJdbcTemplate.query(ticketSql, rs -> {
            String periodo = rs.getString("periodo");
            TicketSummaryDTO dto = new TicketSummaryDTO(
                    rs.getInt("ticket_id"),
                    rs.getObject("dateCallIn", LocalDate.class),
                    rs.getString("descriptionRequest"),
                    rs.getString("descriptionWork"),
                    rs.getString("workComplete"),
                    rs.getString("ticket_type"),
                    rs.getString("building_name")
            );
            ticketsByPeriod.computeIfAbsent(periodo, k -> new ArrayList<>()).add(dto);
        }, fromDate);

        // 4. Combinar por periodo
        Set<String> allPeriods = new TreeSet<>(Comparator.reverseOrder());
        allPeriods.addAll(invoicesByPeriod.keySet());
        allPeriods.addAll(ticketsByPeriod.keySet());

        List<ConsolidatedViewDTO> result = new ArrayList<>();
        for(String periodo: allPeriods){
            result.add(new ConsolidatedViewDTO(
                    periodo,
                    invoicesByPeriod.getOrDefault(periodo, Collections.emptyList()),
                    ticketsByPeriod.getOrDefault(periodo, Collections.emptyList())
            ));
        }
        return result;
    }
}
