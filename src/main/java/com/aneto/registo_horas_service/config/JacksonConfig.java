package com.aneto.registo_horas_service.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary // Garante que o Spring use ESTA instância e não a padrão
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Suporte a datas (LocalDate, LocalDateTime)
        mapper.registerModule(new JavaTimeModule());

        // 2. CORREÇÃO DO ERRO: Impede que o Jackson falhe ao encontrar objetos "vazios" do Hibernate
        // Isso geralmente resolve o UnsupportedOperationException em proxies.
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 3. Evita que datas sejam escritas como arrays de números [2026, 2, 25]
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // 4. Ignora campos nulos no JSON final
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }
}