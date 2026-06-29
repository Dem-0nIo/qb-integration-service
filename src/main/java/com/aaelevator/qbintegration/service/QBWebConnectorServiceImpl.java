package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.Customer;
import com.aaelevator.qbintegration.entity.Invoice;
import com.aaelevator.qbintegration.entity.InvoiceLine;
import com.aaelevator.qbintegration.entity.SyncLog;
import com.aaelevator.qbintegration.repository.CustomerRepository;
import com.aaelevator.qbintegration.repository.InvoiceLineRepository;
import com.aaelevator.qbintegration.repository.InvoiceRepository;
import com.aaelevator.qbintegration.repository.SyncLogRepository;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@WebService(serviceName = "QBWebConnectorSvc",
        portName = "QBWebConnectorSvcSoap",
        targetNamespace = "http://developer.intuit.com/",
        endpointInterface = "com.aaelevator.qbintegration.service.QBWebConnectorService")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT,
        use = SOAPBinding.Use.LITERAL,
        parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public class QBWebConnectorServiceImpl implements QBWebConnectorService {

    private static final Logger log = LoggerFactory.getLogger(QBWebConnectorServiceImpl.class);
    private static final Logger qbXmlLog = LoggerFactory.getLogger("QB_XML_LOGGER");

    @Autowired private CustomerRepository customerRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceLineRepository invoiceLineRepository;
    @Autowired private SyncLogRepository syncLogRepository;
    @Autowired private SyncLogService syncLogService;
    @Autowired private AuditService auditService;

    @Value("${qbwc.username}")
    private String qbUser;

    @Value("${qbwc.password}")
    private String qbPassword;
    //private static final String QB_USER = "qbuser";
    //private static final String QB_PASSWORD = "Qb$ecure2026!";
    // Fecha de inicio del sync histórico — ajustar según el año más antiguo de QB
    private static final LocalDate HISTORICAL_START = LocalDate.of(2010, 1, 1);
    private String xmlRequest;

    @Override
    public ArrayOfString authenticate(String userName, String password) {
        log.info("QBWC authenticate called - user: {}", userName);
        if (qbUser.equals(userName) && qbPassword.equals(password)) {
            log.info("Authentication successful - returning ticket");
            return new ArrayOfString("valid-ticket-001", "");
        }
        log.warn("Authentication failed for user: {}", userName);
        return new ArrayOfString("", "nvu");
    }

    @Override
    public String sendRequestXML(String ticket, String hcpResponse,
                                 String companyFileName, String country,
                                 int majorVersion, int minorVersion) {
        log.info("sendRequestXML called - ticket: {}, company: {}", ticket, companyFileName);
        try {
            Optional<SyncLog> lastAny = syncLogRepository
                    .findTopByEntityTypeInAndStatusOrderByCompletedAtDesc(
                            List.of("CUSTOMER", "INVOICE"), "SUCCESS");

            boolean doCustomers = lastAny.isEmpty() ||
                    lastAny.get().getEntityType().equals("INVOICE");

            log.info("Last sync: {}, doCustomers: {}",
                    lastAny.map(SyncLog::getEntityType).orElse("NONE"), doCustomers);

            if (doCustomers) {
                // ── CUSTOMER sync ──────────────────────────────────────────────
                log.info("Querying Customers");

                SyncLog syncLog = new SyncLog();
                syncLog.setEntityType("CUSTOMER");
                syncLog.setStatus("IN_PROGRESS");
                syncLog.setStartedAt(LocalDateTime.now());
                // cursor_date null — identifica este registro como request de CUSTOMER
                syncLogRepository.save(syncLog);

                Optional<SyncLog> lastCustomerSync = syncLogRepository
                        .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("CUSTOMER", "SUCCESS");

                String fromModifiedDate = lastCustomerSync
                        .map(s -> s.getCompletedAt().toLocalDate().toString() + "T00:00:00")
                        .orElse("2010-01-01T00:00:00");

                log.info("Customer sync from date: {}", fromModifiedDate);

                xmlRequest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                        "<?qbxml version=\"16.0\"?>" +
                        "<QBXML>" +
                        "<QBXMLMsgsRq onError=\"stopOnError\">" +
                        "<CustomerQueryRq requestID=\"1\">" +
                        "<MaxReturned>9999</MaxReturned>" +
                        "<ActiveStatus>ActiveOnly</ActiveStatus>" +
                        "<FromModifiedDate>" + fromModifiedDate + "</FromModifiedDate>" +
                        "</CustomerQueryRq>" +
                        "</QBXMLMsgsRq>" +
                        "</QBXML>";

                qbXmlLog.debug("=== sendRequestXML [CUSTOMER] ===\n{}", xmlRequest);
                return xmlRequest;

            } else {
                // ── INVOICE sync ───────────────────────────────────────────────
                log.info("Querying Invoices");

                boolean historicalDone = syncLogRepository
                        .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS")
                        .isPresent();

                log.info("Invoice historicalDone: {}", historicalDone);

                if (!historicalDone) {
                    // ── INVOICE HISTÓRICO: paginar por ventanas de 1 mes ──────
                    Optional<SyncLog> historicalInProgress = syncLogRepository
                            .findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS");

                    // Leer cursor actual — inicio del mes a pedir
                    String fromTxnDate = historicalInProgress
                            .map(s -> s.getCursorDate() != null
                                    ? s.getCursorDate().toString()
                                    : HISTORICAL_START.toString())
                            .orElse(HISTORICAL_START.toString());

                    // Fin de la ventana: último día del mismo mes
                    LocalDate fromDate = LocalDate.parse(fromTxnDate);
                    LocalDate toDate = fromDate.plusMonths(1).minusDays(1);
                    String toTxnDate = toDate.toString();

                    // Crear registro INVOICE_HISTORICAL IN_PROGRESS solo si no existe
                    if (historicalInProgress.isEmpty()) {
                        SyncLog historicalLog = new SyncLog();
                        historicalLog.setEntityType("INVOICE_HISTORICAL");
                        historicalLog.setStatus("IN_PROGRESS");
                        historicalLog.setStartedAt(LocalDateTime.now());
                        historicalLog.setCursorDate(HISTORICAL_START);
                        syncLogRepository.save(historicalLog);
                    }

                    // Crear registro INVOICE IN_PROGRESS con cursor_date — marca que es request HISTÓRICO
                    SyncLog syncLog = new SyncLog();
                    syncLog.setEntityType("INVOICE");
                    syncLog.setStatus("IN_PROGRESS");
                    syncLog.setStartedAt(LocalDateTime.now());
                    syncLog.setCursorDate(fromDate); // cursor_date != null → identifica request histórico
                    syncLogRepository.save(syncLog);

                    log.info("Invoice HISTORICAL sync window: {} to {}", fromTxnDate, toTxnDate);
                    qbXmlLog.debug("DEBUG sendRequestXML — cursor leído de BD: {}", fromTxnDate);

                    xmlRequest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<?qbxml version=\"16.0\"?>" +
                            "<QBXML>" +
                            "<QBXMLMsgsRq onError=\"stopOnError\">" +
                            "<InvoiceQueryRq requestID=\"1\">" +
                            "<MaxReturned>9999</MaxReturned>" +
                            "<TxnDateRangeFilter>" +
                            "<FromTxnDate>" + fromTxnDate + "</FromTxnDate>" +
                            "<ToTxnDate>" + toTxnDate + "</ToTxnDate>" +
                            "</TxnDateRangeFilter>" +
                            "<IncludeLineItems>true</IncludeLineItems>" +
                            "</InvoiceQueryRq>" +
                            "</QBXMLMsgsRq>" +
                            "</QBXML>";

                    qbXmlLog.debug("=== sendRequestXML [INVOICE_HISTORICAL] ===\n{}", xmlRequest);
                    return xmlRequest;

                } else {
                    // ── INVOICE INCREMENTAL: ModifiedDateRangeFilter ───────────
                    // Crear registro INVOICE IN_PROGRESS sin cursor_date — marca que es request INCREMENTAL
                    SyncLog syncLog = new SyncLog();
                    syncLog.setEntityType("INVOICE");
                    syncLog.setStatus("IN_PROGRESS");
                    syncLog.setStartedAt(LocalDateTime.now());
                    // cursor_date null — identifica este registro como request INCREMENTAL
                    syncLogRepository.save(syncLog);

                    Optional<SyncLog> lastInvoiceSync = syncLogRepository
                            .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE", "SUCCESS");

                    String fromModifiedDate = lastInvoiceSync
                            .map(s -> s.getCompletedAt().toLocalDate().toString() + "T00:00:00")
                            .orElse("2010-01-01T00:00:00");

                    log.info("Invoice incremental sync from date: {}", fromModifiedDate);

                    xmlRequest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<?qbxml version=\"16.0\"?>" +
                            "<QBXML>" +
                            "<QBXMLMsgsRq onError=\"stopOnError\">" +
                            "<InvoiceQueryRq requestID=\"1\">" +
                            "<MaxReturned>9999</MaxReturned>" +
                            "<ModifiedDateRangeFilter>" +
                            "<FromModifiedDate>" + fromModifiedDate + "</FromModifiedDate>" +
                            "</ModifiedDateRangeFilter>" +
                            "<IncludeLineItems>true</IncludeLineItems>" +
                            "</InvoiceQueryRq>" +
                            "</QBXMLMsgsRq>" +
                            "</QBXML>";

                    qbXmlLog.debug("=== sendRequestXML [INVOICE_INCREMENTAL] ===\n{}", xmlRequest);
                    return xmlRequest;
                }
            }
        } catch (Exception e) {
            log.error("sendRequestXML — DB or system failure, aborting sync cycle: {}", e.getMessage(), e);
            // Retornar string vacío: QBWC interpreta esto como "sin trabajo por ahora"
            // y reintentará en el próximo ciclo de polling
            return "";
        }
    }

    @Override
    @Transactional
    public int receiveResponseXML(String ticket, String response,
                                  String hresult, String message) {
        log.info("receiveResponseXML called - ticket: {}", ticket);

        if (response == null || response.isEmpty()) return 100;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(response)));

            qbXmlLog.debug("DEBUG receiveResponseXML — customerNodes: {}, invoiceNodes: {}",
                    doc.getElementsByTagName("CustomerRet").getLength(),
                    doc.getElementsByTagName("InvoiceRet").getLength());

            // ── Procesar clientes ──────────────────────────────────────────
            NodeList customerNodes = doc.getElementsByTagName("CustomerRet");
            if (customerNodes.getLength() > 0) {
                log.info("Customers received from QB: {}", customerNodes.getLength());
                for (int i = 0; i < customerNodes.getLength(); i++) {
                    Element customerEl = (Element) customerNodes.item(i);
                    String listId = getTagValue(customerEl, "ListID");
                    if (listId == null) continue;

                    Customer customer = customerRepository.findById(listId)
                            .orElse(new Customer());
                    customer.setListId(listId);
                    customer.setFullName(getTagValue(customerEl, "FullName"));
                    customer.setCompanyName(getTagValue(customerEl, "CompanyName"));
                    customer.setFirstName(getTagValue(customerEl, "FirstName"));
                    customer.setLastName(getTagValue(customerEl, "LastName"));
                    customer.setEmail(getTagValue(customerEl, "Email"));
                    customer.setPhone(getTagValue(customerEl, "Phone"));
                    customer.setIsActive("true".equals(getTagValue(customerEl, "IsActive")));
                    String balance = getTagValue(customerEl, "Balance");
                    if (balance != null) customer.setBalance(new BigDecimal(balance));
                    String totalBalance = getTagValue(customerEl, "TotalBalance");
                    if (totalBalance != null) customer.setTotalBalance(new BigDecimal(totalBalance));
                    customer.setSyncedAt(LocalDateTime.now());
                    customerRepository.save(customer);
                }
            }
            // Siempre cerrar el sync log CUSTOMER, haya o no resultados
            syncLogService.closeSyncLog("CUSTOMER", customerNodes.getLength());

            // ── Detectar tipo de request por cursor_date del INVOICE IN_PROGRESS ──
            // cursor_date != null → request histórico
            // cursor_date == null → request de customers o incremental
            boolean wasHistoricalRequest = syncLogRepository
                    .findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE", "IN_PROGRESS")
                    .map(s -> s.getCursorDate() != null)
                    .orElse(false);

            qbXmlLog.debug("DEBUG receiveResponseXML — wasHistoricalRequest: {}", wasHistoricalRequest);

            // ── Procesar facturas ──────────────────────────────────────────
            NodeList invoiceNodes = doc.getElementsByTagName("InvoiceRet");
            if (invoiceNodes.getLength() > 0) {
                log.info("Invoices received from QB: {}", invoiceNodes.getLength());
                for (int i = 0; i < invoiceNodes.getLength(); i++) {
                    Element invoiceEl = (Element) invoiceNodes.item(i);
                    String txnId = getTagValue(invoiceEl, "TxnID");
                    if (txnId == null) continue;

                    Invoice invoice = invoiceRepository.findById(txnId)
                            .orElse(new Invoice());
                    invoice.setTxnId(txnId);
                    invoice.setTxnNumber(getTagValue(invoiceEl, "TxnNumber"));
                    invoice.setRefNumber(getTagValue(invoiceEl, "RefNumber"));
                    invoice.setCustomerListId(getTagValue(
                            (Element) invoiceEl.getElementsByTagName("CustomerRef").item(0), "ListID"));
                    invoice.setCustomerFullName(getTagValue(
                            (Element) invoiceEl.getElementsByTagName("CustomerRef").item(0), "FullName"));
                    invoice.setIsPaid("true".equals(getTagValue(invoiceEl, "IsPaid")));
                    invoice.setSyncedAt(LocalDateTime.now());

                    String txnDate = getTagValue(invoiceEl, "TxnDate");
                    if (txnDate != null) invoice.setTxnDate(LocalDate.parse(txnDate));

                    String dueDate = getTagValue(invoiceEl, "DueDate");
                    if (dueDate != null) invoice.setDueDate(LocalDate.parse(dueDate));

                    String subtotal = getTagValue(invoiceEl, "Subtotal");
                    if (subtotal != null) invoice.setSubtotal(new BigDecimal(subtotal));

                    String balanceRemaining = getTagValue(invoiceEl, "BalanceRemaining");
                    if (balanceRemaining != null) invoice.setBalanceRemaining(new BigDecimal(balanceRemaining));

                    invoice.getLines().clear();
                    invoiceRepository.saveAndFlush(invoice);

                    NodeList lineNodes = invoiceEl.getElementsByTagName("InvoiceLineRet");
                    for (int j = 0; j < lineNodes.getLength(); j++) {
                        Element lineEl = (Element) lineNodes.item(j);
                        String txnLineId = getTagValue(lineEl, "TxnLineID");
                        if (txnLineId == null) continue;

                        InvoiceLine line = new InvoiceLine();
                        line.setTxnLineId(txnLineId);
                        line.setInvoice(invoice);
                        line.setDescription(getTagValue(lineEl, "Desc"));

                        String qty = getTagValue(lineEl, "Quantity");
                        if (qty != null) line.setQuantity(new BigDecimal(qty));

                        String rate = getTagValue(lineEl, "Rate");
                        if (rate != null) line.setRate(new BigDecimal(rate));

                        String amount = getTagValue(lineEl, "Amount");
                        if (amount != null) line.setAmount(new BigDecimal(amount));

                        invoice.getLines().add(line);
                    }
                    invoiceRepository.save(invoice);
                }

                int count = invoiceNodes.getLength();

                // Solo avanzar cursor histórico si este request era histórico
                if (wasHistoricalRequest) {
                    boolean historicalDone = syncLogRepository
                            .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS")
                            .isPresent();

                    if (!historicalDone) {
                        syncLogRepository
                                .findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS")
                                .ifPresent(sl -> {
                                    LocalDate currentCursor = sl.getCursorDate() != null
                                            ? sl.getCursorDate()
                                            : HISTORICAL_START;
                                    LocalDate nextCursor = currentCursor.plusMonths(1);
                                    sl.setCursorDate(nextCursor);
                                    syncLogRepository.save(sl);
                                    qbXmlLog.debug("Historical batch done ({} invoices), cursor advanced from {} to {}",
                                            count, currentCursor, nextCursor);
                                });
                    }
                }

                // Cerrar el registro INVOICE (nunca el INVOICE_HISTORICAL)
                syncLogService.closeSyncLog("INVOICE", count);

            } else {
                // QB devolvió 0 invoices para esta ventana

                // Solo avanzar cursor histórico si este request era histórico
                if (wasHistoricalRequest) {
                    boolean historicalDone = syncLogRepository
                            .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE_HISTORICAL", "SUCCESS")
                            .isPresent();

                    if (!historicalDone) {
                        syncLogRepository
                                .findTopByEntityTypeAndStatusOrderByStartedAtDesc("INVOICE_HISTORICAL", "IN_PROGRESS")
                                .ifPresent(sl -> {
                                    LocalDate currentCursor = sl.getCursorDate() != null
                                            ? sl.getCursorDate()
                                            : HISTORICAL_START;

                                    if (currentCursor.isAfter(LocalDate.now())) {
                                        // Cursor pasó la fecha actual — histórico completo
                                        sl.setStatus("SUCCESS");
                                        sl.setCompletedAt(LocalDateTime.now());
                                        sl.setRecordsProcessed(0);
                                        syncLogRepository.save(sl);
                                        qbXmlLog.debug("Historical invoice sync complete — cursor {} passed today",
                                                currentCursor);
                                    } else {
                                        // Mes vacío — avanzar cursor al siguiente mes
                                        LocalDate nextCursor = currentCursor.plusMonths(1);
                                        sl.setCursorDate(nextCursor);
                                        syncLogRepository.save(sl);
                                        qbXmlLog.debug("Historical window empty, cursor advanced from {} to {}",
                                                currentCursor, nextCursor);
                                    }
                                });
                    }
                }

                // Cerrar el registro INVOICE en cualquier caso
                syncLogService.closeSyncLog("INVOICE", 0);
            }

            log.info("Sync completed successfully");
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("receiveResponseXML — DB failure while persisting QB data: {}", e.getMessage(), e);
            auditService.log("DB_ERROR", null, null,
                    "receiveResponseXML DB failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            // Retornar 100 igual — QBWC cierra el ciclo normalmente
            // El sync_log quedará IN_PROGRESS y markStaleAsAbandoned lo limpiará en 1 hora
        } catch (Exception e) {
            log.error("receiveResponseXML — unexpected error processing QB response: {}", e.getMessage(), e);
            auditService.log("INTERNAL_ERROR", null, null,
                    "receiveResponseXML failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return 100;
    }

    @Override
    public String closeConnection(String ticket) {
        log.info("closeConnection called - ticket: {}", ticket);
        return "OK";
    }

    @Override
    public String connectionError(String ticket, String hresult, String message) {
        log.error("connectionError - ticket: {}, hresult: {}, message: {}",
                ticket, hresult, message);
        auditService.log("QBWC_CONNECTION_ERROR", null, null,
                "hresult=" + hresult + " | message=" + message + " | ticket=" + ticket);
        return "done";
    }

    @Override
    public String getLastError(String ticket) {
        log.info("getLastError called - ticket: {}", ticket);
        return "";
    }

    @Override
    public String serverVersion() {
        return "1.0.0";
    }

    @Override
    public String clientVersion(String version) {
        log.info("clientVersion called - version: {}", version);
        return "";
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getParentNode() == element) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}