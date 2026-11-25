package com.aneto.registo_horas_service.queue;

import com.aneto.registo_horas_service.config.RabbitMQConfig;
import com.aneto.registo_horas_service.dto.EmailAttachmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishEmailWithAttachment(
            String to,
            String subject,
            String body,
            String attachmentName,
            byte[] pdfBytes
    ) {
        EmailAttachmentEvent event = EmailAttachmentEvent.builder()
                .to(to)
                .subject(subject)
                .body(body)
                .attachmentName(attachmentName)
                .attachmentBase64(Base64.getEncoder().encodeToString(pdfBytes))
                .contentType("application/pdf")
                .build();

        log.info("Publicando comprovativo PDF ({}) para: {}", attachmentName, to);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ATTACHMENT_ROUTING_KEY,
                event
        );

        log.info("Comprovativo PDF enviado para a fila RabbitMQ.");
    }
}