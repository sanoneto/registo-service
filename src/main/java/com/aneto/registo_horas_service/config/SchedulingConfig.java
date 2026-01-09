package com.aneto.registo_horas_service.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling // Essencial para ativar o agendamento no Spring
public class SchedulingConfig {

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