package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.dto.ConsolidatedViewDTO;
import com.aaelevator.qbintegration.entity.CustomerMapping;
import com.aaelevator.qbintegration.repository.CustomerMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La lógica bajo prueba (unión y orden descendente de periodos) es 100%
 * código Java, independiente del SQL, así que estos tests no requieren una
 * base de datos real: se simulan las llamadas a ResultSet fila por fila.
 */
@ExtendWith(MockitoExtension.class)
class ConsolidatedViewServiceTest {

    @Mock private CustomerMappingRepository customerMappingRepository;
    @Mock private JdbcTemplate primaryJdbcTemplate;
    @Mock private JdbcTemplate wordPressJdbcTemplate;

    private ConsolidatedViewService service;

    @BeforeEach
    void setUp() {
        service = new ConsolidatedViewService();
        ReflectionTestUtils.setField(service, "customerMappingRepository", customerMappingRepository);
        ReflectionTestUtils.setField(service, "primaryJdbcTemplate", primaryJdbcTemplate);
        ReflectionTestUtils.setField(service, "wordPressJdbcTemplate", wordPressJdbcTemplate);
    }

    private CustomerMapping mapping(int ticketCustomerId) {
        CustomerMapping cm = new CustomerMapping();
        cm.setTicketCustomerId(ticketCustomerId);
        return cm;
    }

    @Test
    @DisplayName("sin mapeos para el qbEmpresa, retorna lista vacía sin consultar ninguna base de datos")
    void getMaintenanceView_noMappings_returnsEmptyListWithoutQuerying() {
        when(customerMappingRepository.findByQbEmpresa("Boheme Condo"))
                .thenReturn(Collections.emptyList());

        List<ConsolidatedViewDTO> result = service.getMaintenanceView("Boheme Condo", 12);

        assertThat(result).isEmpty();
        verifyNoInteractions(primaryJdbcTemplate, wordPressJdbcTemplate);
    }

    @Test
    @DisplayName("combina facturas y tickets del mismo periodo, y ordena los periodos descendente")
    void getMaintenanceView_combinesInvoicesAndTickets_sortedDescending() throws Exception {
        when(customerMappingRepository.findByQbEmpresa("Boheme Condo"))
                .thenReturn(List.of(mapping(42)));

        // Fila de factura: periodo 2026-05
        ResultSet invoiceRow = mock(ResultSet.class);
        when(invoiceRow.getObject("txn_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 5, 10));
        when(invoiceRow.getString("txn_number")).thenReturn("1001");
        when(invoiceRow.getBigDecimal("subtotal")).thenReturn(new BigDecimal("250.00"));
        when(invoiceRow.getBigDecimal("balance_remaining")).thenReturn(BigDecimal.ZERO);
        when(invoiceRow.getBoolean("is_paid")).thenReturn(true);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(invoiceRow);
            return null;
        }).when(primaryJdbcTemplate).query(anyString(), any(RowCallbackHandler.class), eq("Boheme Condo"), any(LocalDate.class));

        // Fila de ticket: periodo 2026-06 (distinto al de la factura)
        ResultSet ticketRow = mock(ResultSet.class);
        when(ticketRow.getString("periodo")).thenReturn("2026-06");
        when(ticketRow.getInt("ticket_id")).thenReturn(555);
        when(ticketRow.getObject("dateCallIn", LocalDate.class)).thenReturn(LocalDate.of(2026, 6, 3));
        when(ticketRow.getString("descriptionRequest")).thenReturn("Mantenimiento mensual");
        when(ticketRow.getString("descriptionWork")).thenReturn("Revisión general");
        when(ticketRow.getString("workComplete")).thenReturn("Y");
        when(ticketRow.getString("ticket_type")).thenReturn("Maintenance");
        when(ticketRow.getString("building_name")).thenReturn("Boheme Tower");

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(ticketRow);
            return null;
        }).when(wordPressJdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(LocalDate.class));

        List<ConsolidatedViewDTO> result = service.getMaintenanceView("Boheme Condo", 12);

        assertThat(result).hasSize(2);
        // Orden descendente: 2026-06 antes que 2026-05
        assertThat(result.get(0).getPeriodo()).isEqualTo("2026-06");
        assertThat(result.get(1).getPeriodo()).isEqualTo("2026-05");

        // El periodo con solo ticket no debe traer facturas fantasma, y viceversa
        assertThat(result.get(0).getFacturas()).isEmpty();
        assertThat(result.get(0).getTickets()).hasSize(1);
        assertThat(result.get(1).getFacturas()).hasSize(1);
        assertThat(result.get(1).getTickets()).isEmpty();
    }

    @Test
    @DisplayName("un periodo con factura Y ticket coincidentes aparece una sola vez con ambos incluidos")
    void getMaintenanceView_samePeriodInvoiceAndTicket_mergedIntoOneEntry() throws Exception {
        when(customerMappingRepository.findByQbEmpresa("A&A Elevator"))
                .thenReturn(List.of(mapping(7)));

        ResultSet invoiceRow = mock(ResultSet.class);
        when(invoiceRow.getObject("txn_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 3, 1));
        when(invoiceRow.getString("txn_number")).thenReturn("2002");
        when(invoiceRow.getBigDecimal("subtotal")).thenReturn(new BigDecimal("500.00"));
        when(invoiceRow.getBigDecimal("balance_remaining")).thenReturn(new BigDecimal("500.00"));
        when(invoiceRow.getBoolean("is_paid")).thenReturn(false);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(invoiceRow);
            return null;
        }).when(primaryJdbcTemplate).query(anyString(), any(RowCallbackHandler.class), eq("A&A Elevator"), any(LocalDate.class));

        ResultSet ticketRow = mock(ResultSet.class);
        when(ticketRow.getString("periodo")).thenReturn("2026-03");
        when(ticketRow.getInt("ticket_id")).thenReturn(9);
        when(ticketRow.getObject("dateCallIn", LocalDate.class)).thenReturn(LocalDate.of(2026, 3, 5));
        when(ticketRow.getString("descriptionRequest")).thenReturn("Servicio semanal");
        when(ticketRow.getString("descriptionWork")).thenReturn("OK");
        when(ticketRow.getString("workComplete")).thenReturn("Y");
        when(ticketRow.getString("ticket_type")).thenReturn("Maintenance");
        when(ticketRow.getString("building_name")).thenReturn("Torre Central");

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(ticketRow);
            return null;
        }).when(wordPressJdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(LocalDate.class));

        List<ConsolidatedViewDTO> result = service.getMaintenanceView("A&A Elevator", 12);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriodo()).isEqualTo("2026-03");
        assertThat(result.get(0).getFacturas()).hasSize(1);
        assertThat(result.get(0).getTickets()).hasSize(1);
    }
}
