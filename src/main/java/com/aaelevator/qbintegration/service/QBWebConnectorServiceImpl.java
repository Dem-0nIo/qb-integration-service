package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.Customer;
import com.aaelevator.qbintegration.entity.Invoice;
import com.aaelevator.qbintegration.entity.InvoiceLine;
import com.aaelevator.qbintegration.repository.InvoiceLineRepository;
import com.aaelevator.qbintegration.repository.InvoiceRepository;
import com.aaelevator.qbintegration.entity.SyncLog;
import com.aaelevator.qbintegration.repository.SyncLogRepository;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.jws.soap.SOAPBinding;
import com.aaelevator.qbintegration.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private int syncCycle = 0;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Autowired
    private SyncLogService syncLogService;

    private static final String QB_USER = "qbuser";
    private static final String QB_PASSWORD = "Qb$ecure2026!";

    @Override
    public ArrayOfString authenticate(String userName, String password) {
        log.info("QBWC authenticate called - user: {}", userName);
        if (QB_USER.equals(userName) && QB_PASSWORD.equals(password)) {
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

        syncCycle++;

        if (syncCycle % 2 == 1) {
            log.info("Sync cycle {}: querying Customers", syncCycle);

            SyncLog syncLog = new SyncLog();
            syncLog.setEntityType("CUSTOMER");
            syncLog.setStatus("IN_PROGRESS");
            syncLog.setStartedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            Optional<SyncLog> lastSync = syncLogRepository
                    .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("CUSTOMER", "SUCCESS");

            String fromModifiedDate = lastSync
                    .map(s -> s.getCompletedAt().toLocalDate().toString() + "T00:00:00")
                    .orElse("1970-01-01T00:00:00");

            log.info("Customer sync from date: {}", fromModifiedDate);

            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<?qbxml version=\"16.0\"?>" +
                    "<QBXML>" +
                    "<QBXMLMsgsRq onError=\"stopOnError\">" +
                    "<CustomerQueryRq requestID=\"1\">" +
                    "<MaxReturned>100</MaxReturned>" +
                    "<ActiveStatus>ActiveOnly</ActiveStatus>" +
                    "<FromModifiedDate>" + fromModifiedDate + "</FromModifiedDate>" +
                    "</CustomerQueryRq>" +
                    "</QBXMLMsgsRq>" +
                    "</QBXML>";

        } else {
            log.info("Sync cycle {}: querying Invoices", syncCycle);

            SyncLog syncLog = new SyncLog();
            syncLog.setEntityType("INVOICE");
            syncLog.setStatus("IN_PROGRESS");
            syncLog.setStartedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            Optional<SyncLog> lastSync = syncLogRepository
                    .findTopByEntityTypeAndStatusOrderByCompletedAtDesc("INVOICE", "SUCCESS");

            String fromModifiedDate = lastSync
                    .map(s -> s.getCompletedAt().toLocalDate().toString() + "T00:00:00")
                    .orElse("1970-01-01T00:00:00");

            log.info("Invoice sync from date: {}", fromModifiedDate);

            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<?qbxml version=\"16.0\"?>" +
                    "<QBXML>" +
                    "<QBXMLMsgsRq onError=\"stopOnError\">" +
                    "<InvoiceQueryRq requestID=\"1\">" +
                    "<MaxReturned>100</MaxReturned>" +
                    "<ModifiedDateRangeFilter>" +
                    "<FromModifiedDate>" + fromModifiedDate + "</FromModifiedDate>" +
                    "</ModifiedDateRangeFilter>" +
                    "<IncludeLineItems>true</IncludeLineItems>" +
                    "</InvoiceQueryRq>" +
                    "</QBXMLMsgsRq>" +
                    "</QBXML>";
        }
    }

    /*@Override
    public int receiveResponseXML(String ticket, String response,
                                  String hresult, String message) {
        log.info("receiveResponseXML called - ticket: {}", ticket);

        if (hresult != null && !hresult.isEmpty()) {
            log.error("QB error - hresult: {}, message: {}", hresult, message);
            return -1;
        }

        log.info("Response received successfully");
        log.info("QB Response: {}", response);

        return 100;
    }*/
    @Override
    @Transactional
    public int receiveResponseXML(String ticket, String response,
                                  String hresult, String message) {
        log.info("receiveResponseXML called - ticket: {}", ticket);

        if (response != null && !response.isEmpty()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(response)));

                // Procesar clientes si hay CustomerRet
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
                    // ← cierre del SyncLog FUERA del loop, al final del if
                    syncLogService.closeSyncLog("CUSTOMER", customerNodes.getLength());                }

                // Procesar facturas si hay InvoiceRet
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
                    // ← cierre del SyncLog FUERA del loop, al final del if
                    syncLogService.closeSyncLog("INVOICE", invoiceNodes.getLength());                }

                log.info("Sync completed successfully");
            } catch (Exception e) {
                log.error("Error parsing QB response: {}", e.getMessage(), e);
                log.error("Exception type: {}", e.getClass().getName());
            }
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

    private String getTagValue(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getParentNode() == element) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    @Override
    public String clientVersion(String version) {
        log.info("clientVersion called - version: {}", version);
        return "";
    }

}