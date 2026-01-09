package com.aneto.registo_horas_service.config;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.security.Security;

@Configuration
@EnableScheduling // Essencial para ativar o agendamento no Spring
public class SchedulingConfig {

    // Isto garante que o provedor BC é registado assim que o Spring inicia
    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Define quantos alertas o sistema pode processar AO MESMO TEMPO
        scheduler.setPoolSize(10);
        // Nome das threads para facilitar o debug se houver erros
        scheduler.setThreadNamePrefix("AlertaThread-");
        // Garante que as tarefas terminem antes de desligar o servidor
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }
}