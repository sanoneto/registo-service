package com.aneto.registo_horas_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ... (Outros imports)

@Configuration
public class RabbitMQConfig {

    // 🔑 CORREÇÃO: Defina a constante da fila como public static final
    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String EMAIL_ROUTING_KEY = "email.send";


    @Bean("rabbitJsonConverter") // Dê um nome específico ao bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 1. Declaração da Queue (Fila)
    @Bean
    public Queue emailQueue() {
        // Usa a constante para garantir consistência
        return new Queue(EMAIL_QUEUE, true);
    }

    // 2. Declaração do Exchange (Trocador)
    @Bean
    public Exchange emailExchange() {
        return new DirectExchange(EMAIL_EXCHANGE, true, false);
    }

    // 3. Vinculação (Binding)
    @Bean
    public Binding bindingEmail(Queue emailQueue, Exchange emailExchange) {
        return BindingBuilder
                .bind(emailQueue)
                .to(emailExchange)
                .with(EMAIL_ROUTING_KEY)
                .noargs();
    }

    // ... (Outras configurações, se houver)
}