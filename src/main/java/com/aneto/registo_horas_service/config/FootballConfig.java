package com.aneto.registo_horas_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FootballConfig {

    @Value("${Football.Data.API_TOKEN}")
    private String API_TOKEN;

    @Value("${Football.Data.url}")
    private String BASE_URL;

    @Bean
    public WebClient footballWebClient() {
        return WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Auth-Token", API_TOKEN)
                .build();
    }
}