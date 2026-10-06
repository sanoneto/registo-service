package com.aneto.registo_horas_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${url.micro_service_auth}")
    private String urlMicroServicoAuth;

    @Value("${internal.api-key}")
    private String internalKey;

    @Bean
    public WebClient targetServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(urlMicroServicoAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-Key", internalKey)
                .build();
    }
}