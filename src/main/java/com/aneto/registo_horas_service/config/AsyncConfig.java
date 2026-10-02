package com.aneto.registo_horas_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * NOVO: habilita @Async e define um TaskExecutor dedicado para a geração de planos.
 *
 * IMPORTANTE: antes de adicionar esta classe, confirma que @EnableAsync não está já
 * declarado noutro @Configuration do projeto — duas declarações não quebram o arranque,
 * mas são redundantes e vale a pena consolidar numa só.
 *
 * O pool é propositadamente pequeno e separado do pool de threads do Tomcat: a geração
 * de planos pode demorar 1-2 minutos (chamadas a um modelo de IA + retries), e não
 * queremos que isso compita por threads com pedidos HTTP normais (leituras, login, etc.).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "trainingTaskExecutor")
    public Executor trainingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Ajusta conforme o volume esperado de gerações em simultâneo e os limites de
        // rate-limit do provider de IA — não faz sentido um pool maior do que o número
        // de chamadas concorrentes que o provider aceita sem erro 429.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("training-gen-");
        executor.initialize();
        return executor;
    }
}