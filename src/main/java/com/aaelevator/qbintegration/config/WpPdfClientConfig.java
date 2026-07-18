package com.aaelevator.qbintegration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WpPdfClientConfig {

    @Bean
    public RestClient wpPdfRestClient(
            @Value("${wordpress.pdf.base-url}") String baseUrl,
            @Value("${wordpress.pdf.internal-token}") String internalToken) {

        // Fábrica de conexiones HTTP con timeouts explícitos
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));   // máximo para establecer la conexión TCP/TLS
        factory.setReadTimeout(Duration.ofSeconds(30));     // máximo esperando la respuesta (la generación del PDF)

        return RestClient.builder()
                .baseUrl(baseUrl)                              // https://apps.aaelevator.net/wordpress (de la env var)
                .defaultHeader("X-Internal-Token", internalToken) // el secreto va en TODAS las requests de este cliente
                .requestFactory(factory)
                .build();
    }
}