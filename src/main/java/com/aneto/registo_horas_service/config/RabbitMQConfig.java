package com.aneto.registo_horas_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String EMAIL_ROUTING_KEY = "email.send";

    public static final String EMAIL_ATTACHMENT_QUEUE = "email.attachment.queue";
    public static final String EMAIL_ATTACHMENT_ROUTING_KEY = "email.attachment.routingKey";

    @Bean("rabbitJsonConverter")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue emailQueue() {
        return new Queue(EMAIL_QUEUE, true);
    }

    @Bean
    public Queue emailAttachmentQueue() {
        return new Queue(EMAIL_ATTACHMENT_QUEUE, true);
    }

    @Bean
    public Exchange emailExchange() {
        return new DirectExchange(EMAIL_EXCHANGE, true, false);
    }

    @Bean
    public Binding bindingEmail(Queue emailQueue, Exchange emailExchange) {
        return BindingBuilder
                .bind(emailQueue)
                .to(emailExchange)
                .with(EMAIL_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Binding bindingEmailAttachment(Queue emailAttachmentQueue, Exchange emailExchange) {
        return BindingBuilder
                .bind(emailAttachmentQueue)
                .to(emailExchange)
                .with(EMAIL_ATTACHMENT_ROUTING_KEY)
                .noargs();
    }
}