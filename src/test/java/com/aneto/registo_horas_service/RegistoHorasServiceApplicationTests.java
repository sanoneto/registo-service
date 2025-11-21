package com.aneto.registo_horas_service;

import com.aneto.registo_horas_service.service.RegistosHorasService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest
public class RegistoHorasServiceApplicationTests {

    // ...

    @TestConfiguration // Indica que esta classe fornece beans específicos para o teste
    static class TestConfig {
        @Bean // O bean injetado no contexto de teste será este Mock
        public RegistosHorasService registosHorasService() {
            // Cria e retorna o mock do Mockito
            return Mockito.mock(RegistosHorasService.class);
        }
    }
}