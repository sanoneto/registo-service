package com.aneto.registo_horas_service.service;


import com.aneto.registo_horas_service.dto.request.PushSubscriptionDTO;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Value("${vapid.public.key}")
    private String publicKey;

    @Value("${vapid.private.key}")
    private String privateKey;

    @Value("${vapid.subject}")
    private String subject;

    // REMOVIDO O STATIC para as chaves funcionarem
    public void sendPushNotification(PushSubscriptionDTO subDto, String messageJson) {
        try {
            PushService pushService = new PushService(publicKey, privateKey, subject);

            Subscription sub = new Subscription(
                    subDto.getEndpoint(),
                    new Subscription.Keys(subDto.getKeys().getP256dh(), subDto.getKeys().getAuth())
            );

            Notification notification = new Notification(sub, messageJson);
            pushService.send(notification);
            System.out.println("Push enviado!");
        } catch (Exception e) {
            System.err.println("Erro ao enviar push: " + e.getMessage());
        }
    }
}