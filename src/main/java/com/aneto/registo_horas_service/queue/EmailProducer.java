package com.aneto.registo_horas_service.queue;


import com.aneto.registo_horas_service.config.RabbitMQConfig;
import com.aneto.registo_horas_service.dto.request.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailProducer {

    private static final Logger log = LoggerFactory.getLogger(EmailProducer.class);
    private final RabbitTemplate rabbitTemplate;

    public void sendRegistrationEmail(String recipientName, String recipientEmail) {

        // Constrói o DTO específico que a fila espera
        EmailRequest emailRequest = new EmailRequest(
                recipientName,
                recipientEmail,
                "Bem-vindo(a) ao Registo de Horas Aneto! O seu registo foi concluído com sucesso."
        );

        log.info("Enviando mensagem para a Exchange: {} com Routing Key: {}", RabbitMQConfig.EMAIL_EXCHANGE, RabbitMQConfig.EMAIL_ROUTING_KEY);

        // Envia a mensagem para a fila
        rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_EXCHANGE, RabbitMQConfig.EMAIL_ROUTING_KEY, emailRequest);

        log.info("Mensagem de registo de e-mail enviada para a fila: {}", emailRequest.email());
    }
}