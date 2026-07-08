package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.Customer;
import com.aaelevator.qbintegration.entity.Invoice;
import com.aaelevator.qbintegration.entity.SyncLog;
import com.aaelevator.qbintegration.repository.CustomerRepository;
import com.aaelevator.qbintegration.repository.InvoiceLineRepository;
import com.aaelevator.qbintegration.repository.InvoiceRepository;
import com.aaelevator.qbintegration.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NOTA DE ASUNCIONES: estos tests asumen que SyncLog expone los getters/setters
 * usados literalmente en QBWebConnectorServiceImpl.java (getEntityType,
 * setEntityType, getStatus, setStatus, getStartedAt, setStartedAt,
 * getCompletedAt, setCompletedAt, getCursorDate, setCursorDate,
 * setRecordsProcessed) y que SyncLog tiene un constructor sin argumentos.
 * Si el nombre real difiere, solo hay que ajustar el helper buildSyncLog().
 */
@ExtendWith(MockitoExtension.class)
class QBWebConnectorServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private SyncLogService syncLogService;
    @Mock private AuditService auditService;

    private QBWebConnectorServiceImpl service;

    private static final LocalDate HISTORICAL_START = LocalDate.of(2010, 1, 1);

    @BeforeEach
    void setUp() {
        service = new QBWebConnectorServiceImpl();
        ReflectionTestUtils.setField(service, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(service, "invoiceRepository", invoiceRepository);
        ReflectionTestUtils.setField(service, "invoiceLineRepository", invoiceLineRepository);
        ReflectionTestUtils.setField(service, "syncLogRepository", syncLogRepository);
        ReflectionTestUtils.setField(service, "syncLogService", syncLogService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "qbUser", "qbuser");
        ReflectionTestUtils.setField(service, "qbPassword", "qbpass");
    }

    private SyncLog buildSyncLog(String entityType, String status, LocalDateTime completedAt, LocalDate cursorDate) {
        SyncLog log = new SyncLog();
        log.setEntityType(entityType);
        log.setStatus(status);
        if (completedAt != null) log.setCompletedAt(completedAt);
        if (cursorDate != null) log.setCursorDate(cursorDate);
        return log;
    }

    // ───────────────────────── sendRequestXML ─────────────────────────

    @Test
    @DisplayName("sin sync previo, primero se piden CUSTOMER (no INVOICE)")
    void sendRequestXML_noPriorSync_requestsCustomersFirst() {
        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("CUSTOMER", "SUCCESS"))
                .thenReturn(Optional.empty());

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).contains("<CustomerQueryRq");
        assertThat(xml).contains("<FromModifiedDate>2010-01-01T00:00:00</FromModifiedDate>");

        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEntityType()).isEqualTo("CUSTOMER");
        assertThat(captor.getValue().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("si el último sync fue INVOICE, alterna de vuelta a CUSTOMER")
    void sendRequestXML_lastSyncWasInvoice_alternatesBackToCustomers() {
        SyncLog lastInvoice = buildSyncLog("INVOICE", "SUCCESS", LocalDateTime.now(), null);
        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.of(lastInvoice));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("CUSTOMER", "SUCCESS"))
                .thenReturn(Optional.empty());

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).contains("<CustomerQueryRq");
    }

    @Test
    @DisplayName("si el último sync fue CUSTOMER y no hay histórico previo, arranca ventana histórica en HISTORICAL_START")
    void sendRequestXML_historicalFirstRun_startsAtHistoricalStart() {
        SyncLog lastCustomer = buildSyncLog("CUSTOMER", "SUCCESS", LocalDateTime.now(), null);
        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.of(lastCustomer));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS"))
                .thenReturn(Optional.empty());

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).contains("<FromTxnDate>2010-01-01</FromTxnDate>");
        assertThat(xml).contains("<ToTxnDate>2010-01-31</ToTxnDate>");

        // Debe crear el registro INVOICE_HISTORICAL nuevo Y el registro INVOICE de este ciclo
        verify(syncLogRepository, times(2)).save(any(SyncLog.class));
    }

    @Test
    @DisplayName("con histórico en progreso, continúa la ventana desde el cursor guardado (no reinicia)")
    void sendRequestXML_historicalInProgress_continuesFromCursor() {
        SyncLog lastCustomer = buildSyncLog("CUSTOMER", "SUCCESS", LocalDateTime.now(), null);
        SyncLog inProgress = buildSyncLog("INVOICE_HISTORICAL", "IN_PROGRESS", null, LocalDate.of(2015, 6, 1));

        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.of(lastCustomer));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS"))
                .thenReturn(Optional.of(inProgress));

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        // Junio tiene 30 días: ventana debe ser 2015-06-01 a 2015-06-30, no reiniciar a HISTORICAL_START
        assertThat(xml).contains("<FromTxnDate>2015-06-01</FromTxnDate>");
        assertThat(xml).contains("<ToTxnDate>2015-06-30</ToTxnDate>");

        // El registro INVOICE_HISTORICAL ya existe: solo se guarda el nuevo registro INVOICE de este ciclo
        verify(syncLogRepository, times(1)).save(any(SyncLog.class));
    }

    @Test
    @DisplayName("con histórico completo, usa ModifiedDateRangeFilter incremental, no TxnDateRangeFilter")
    void sendRequestXML_historicalDone_usesIncrementalModifiedDateFilter() {
        SyncLog lastCustomer = buildSyncLog("CUSTOMER", "SUCCESS", LocalDateTime.now(), null);
        SyncLog historicalSuccess = buildSyncLog("INVOICE_HISTORICAL", "SUCCESS", LocalDateTime.now(), null);
        SyncLog lastInvoiceSync = buildSyncLog("INVOICE", "SUCCESS",
                LocalDateTime.of(2026, 6, 1, 10, 0), null);

        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.of(lastCustomer));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.of(historicalSuccess));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE", "SUCCESS"))
                .thenReturn(Optional.of(lastInvoiceSync));

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).contains("<ModifiedDateRangeFilter>");
        assertThat(xml).contains("<FromModifiedDate>2026-06-01T00:00:00</FromModifiedDate>");
        assertThat(xml).doesNotContain("<TxnDateRangeFilter>");
    }

    @Test
    @DisplayName("modo incremental sin sync de INVOICE previo, usa 2010-01-01 por defecto")
    void sendRequestXML_incrementalNoPriorInvoiceSync_defaultsTo2010() {
        SyncLog lastCustomer = buildSyncLog("CUSTOMER", "SUCCESS", LocalDateTime.now(), null);
        SyncLog historicalSuccess = buildSyncLog("INVOICE_HISTORICAL", "SUCCESS", LocalDateTime.now(), null);

        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                List.of("CUSTOMER", "INVOICE"), "SUCCESS")).thenReturn(Optional.of(lastCustomer));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.of(historicalSuccess));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE", "SUCCESS"))
                .thenReturn(Optional.empty());

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).contains("<FromModifiedDate>2010-01-01T00:00:00</FromModifiedDate>");
    }

    @Test
    @DisplayName("si el repositorio falla, se devuelve string vacío en lugar de propagar la excepción")
    void sendRequestXML_repositoryThrows_returnsEmptyStringGracefully() {
        when(syncLogRepository.findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(anyList(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        String xml = service.sendRequestXML("ticket", "", "company.qbw", "US", 16, 0);

        assertThat(xml).isEmpty();
    }

    // ───────────────────────── receiveResponseXML ─────────────────────────

    @Test
    @DisplayName("respuesta null o vacía retorna 100 sin tocar ningún repositorio")
    void receiveResponseXML_nullOrEmptyResponse_shortCircuits() {
        assertThat(service.receiveResponseXML("t", null, "0", "")).isEqualTo(100);
        assertThat(service.receiveResponseXML("t", "", "0", "")).isEqualTo(100);
        verifyNoInteractions(syncLogService, customerRepository, invoiceRepository);
    }

    @Test
    @DisplayName("closeSyncLog(CUSTOMER) se llama incluso con 0 clientes en la respuesta (guarda contra alternación rota)")
    void receiveResponseXML_zeroCustomers_stillClosesCustomerSyncLog() {
        String response = "<QBXML><QBXMLMsgsRq><CustomerQueryRs/></QBXMLMsgsRq></QBXML>";
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS"))
                .thenReturn(Optional.empty());

        service.receiveResponseXML("t", response, "0", "");

        verify(syncLogService).closeSyncLog("CUSTOMER", 0);
    }

    @Test
    @DisplayName("lote histórico con facturas avanza el cursor exactamente un mes")
    void receiveResponseXML_historicalBatchWithInvoices_advancesCursorByOneMonth() {
        String response = "<QBXML><QBXMLMsgsRq><InvoiceQueryRs>" +
                "<InvoiceRet>" +
                "<TxnID>TX-1</TxnID><TxnNumber>1001</TxnNumber>" +
                "<CustomerRef><ListID>C-1</ListID><FullName>Boheme Condo</FullName></CustomerRef>" +
                "<IsPaid>false</IsPaid>" +
                "<TxnDate>2015-06-15</TxnDate><DueDate>2015-07-15</DueDate>" +
                "<Subtotal>100.00</Subtotal><BalanceRemaining>100.00</BalanceRemaining>" +
                "</InvoiceRet>" +
                "</InvoiceQueryRs></QBXMLMsgsRq></QBXML>";

        SyncLog invoiceInProgress = buildSyncLog("INVOICE", "IN_PROGRESS", null, LocalDate.of(2015, 6, 1));
        SyncLog historicalInProgress = buildSyncLog("INVOICE_HISTORICAL", "IN_PROGRESS", null, LocalDate.of(2015, 6, 1));

        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS"))
                .thenReturn(Optional.of(invoiceInProgress));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS"))
                .thenReturn(Optional.of(historicalInProgress));
        when(invoiceRepository.findById("TX-1")).thenReturn(Optional.empty());
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        int result = service.receiveResponseXML("t", response, "0", "");

        assertThat(result).isEqualTo(100);
        assertThat(historicalInProgress.getCursorDate()).isEqualTo(LocalDate.of(2015, 7, 1));
        verify(syncLogService).closeSyncLog("INVOICE", 1);
    }

    @Test
    @DisplayName("ventana histórica vacía (sin facturas) avanza el cursor un mes sin marcar SUCCESS")
    void receiveResponseXML_emptyHistoricalWindow_advancesCursorWithoutCompleting() {
        String response = "<QBXML><QBXMLMsgsRq><InvoiceQueryRs/></QBXMLMsgsRq></QBXML>";

        LocalDate cursor = LocalDate.now().minusMonths(6); // claramente antes de hoy
        SyncLog invoiceInProgress = buildSyncLog("INVOICE", "IN_PROGRESS", null, cursor);
        SyncLog historicalInProgress = buildSyncLog("INVOICE_HISTORICAL", "IN_PROGRESS", null, cursor);

        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS"))
                .thenReturn(Optional.of(invoiceInProgress));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS"))
                .thenReturn(Optional.of(historicalInProgress));

        service.receiveResponseXML("t", response, "0", "");

        assertThat(historicalInProgress.getCursorDate()).isEqualTo(cursor.plusMonths(1));
        assertThat(historicalInProgress.getStatus()).isEqualTo("IN_PROGRESS");
        verify(syncLogService).closeSyncLog("INVOICE", 0);
    }

    @Test
    @DisplayName("cuando el cursor histórico pasa la fecha actual, se marca el histórico como SUCCESS")
    void receiveResponseXML_cursorPastToday_marksHistoricalComplete() {
        String response = "<QBXML><QBXMLMsgsRq><InvoiceQueryRs/></QBXMLMsgsRq></QBXML>";

        LocalDate futureCursor = LocalDate.now().plusDays(10); // ya pasó "hoy" en la iteración anterior
        SyncLog invoiceInProgress = buildSyncLog("INVOICE", "IN_PROGRESS", null, futureCursor);
        SyncLog historicalInProgress = buildSyncLog("INVOICE_HISTORICAL", "IN_PROGRESS", null, futureCursor);

        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS"))
                .thenReturn(Optional.of(invoiceInProgress));
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS"))
                .thenReturn(Optional.empty());
        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS"))
                .thenReturn(Optional.of(historicalInProgress));

        service.receiveResponseXML("t", response, "0", "");

        assertThat(historicalInProgress.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("una respuesta incremental (sin cursor) nunca toca el cursor histórico")
    void receiveResponseXML_incrementalRequest_neverTouchesHistoricalCursor() {
        String response = "<QBXML><QBXMLMsgsRq><InvoiceQueryRs/></QBXMLMsgsRq></QBXML>";

        SyncLog invoiceInProgress = buildSyncLog("INVOICE", "IN_PROGRESS", null, null); // cursor_date null = incremental

        when(syncLogRepository.findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS"))
                .thenReturn(Optional.of(invoiceInProgress));

        service.receiveResponseXML("t", response, "0", "");

        verify(syncLogRepository, never())
                .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS");
        verify(syncLogRepository, never())
                .findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS");
        verify(syncLogService).closeSyncLog("INVOICE", 0);
    }

    @Test
    @DisplayName("un fallo de base de datos al persistir se captura, se audita y no propaga (retorna 100 igual)")
    void receiveResponseXML_dbFailure_isCaughtAuditedAndDoesNotPropagate() {
        String response = "<QBXML><QBXMLMsgsRq><CustomerQueryRs>" +
                "<CustomerRet><ListID>C-1</ListID><FullName>Cliente X</FullName></CustomerRet>" +
                "</CustomerQueryRs></QBXMLMsgsRq></QBXML>";

        when(customerRepository.findById("C-1"))
                .thenThrow(new DataAccessResourceFailureException("conexión perdida"));

        int result = service.receiveResponseXML("t", response, "0", "");

        assertThat(result).isEqualTo(100);
        verify(auditService).log(eq("DB_ERROR"), isNull(), isNull(), anyString());
    }
}
