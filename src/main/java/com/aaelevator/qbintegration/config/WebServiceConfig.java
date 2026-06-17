package com.aaelevator.qbintegration.config;

import com.aaelevator.qbintegration.service.QBWebConnectorServiceImpl;
import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebServiceConfig {

    private final Bus bus;
    private final QBWebConnectorServiceImpl qbWebConnectorService;

    public WebServiceConfig(Bus bus, QBWebConnectorServiceImpl qbWebConnectorService) {
        this.bus = bus;
        this.qbWebConnectorService = qbWebConnectorService;
    }

    @Bean
    public Endpoint qbWebConnectorEndpoint() {
        EndpointImpl endpoint = new EndpointImpl(bus, qbWebConnectorService);
        endpoint.setWsdlLocation("classpath:QBWebConnectorService.wsdl");
        endpoint.publish("/qbwc");
        return endpoint;
    }
}