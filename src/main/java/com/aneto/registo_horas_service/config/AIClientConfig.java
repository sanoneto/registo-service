package com.aneto.registo_horas_service.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AIClientConfig {

    // Lendo a chave de segurança
    @Value("${ai.api.key}")
    private String aiApiKey;

    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
                // URL Base Correta para a OpenAI
                .baseUrl("https://api.openai.com/v1")
                // Autenticação Bearer (CRÍTICO para evitar o 401)
                .defaultHeader("Authorization", "Bearer " + aiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}