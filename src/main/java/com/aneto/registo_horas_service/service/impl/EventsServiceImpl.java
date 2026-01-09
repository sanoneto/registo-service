package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.request.PushSubscriptionDTO;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.mapper.EventsMapper;
import com.aneto.registo_horas_service.models.Evento;
import com.aneto.registo_horas_service.repository.EventoRepository;
import com.aneto.registo_horas_service.service.EventsService;
import com.aneto.registo_horas_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventoRepository repository;
    private final EventsMapper mapper;
    private final TaskScheduler taskScheduler; // Adicionado final
    private final NotificationService notificationService; // Injetado aqui
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public EventsResponse criarEvento(EventRequest request) {
        Evento evento = mapper.toEntity(request);
        // Definimos explicitamente como falso ao criar
        evento.setAlertConfirmed(false);
        Evento salvo = repository.save(evento);

        if (request.sendAlert() && request.notificationSubscription() != null) {
            // Passamos o ID do objeto SALVO para podermos verificar na DB depois
            agendarAlertaComRepeticao(salvo.getId(), request);
        }

        return mapper.toResponse(salvo);
    }

    @Override
    public void agendarAlerta(EventRequest request) {
        LocalDateTime dataHoraEvento = LocalDateTime.of(request.referenceDate(), request.startTime());

        Instant momentoAlerta = dataHoraEvento
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .minus(0, ChronoUnit.MINUTES);

        // Se o agendamento for feito para menos de 15 min a partir de agora, envia logo
        if (momentoAlerta.isBefore(Instant.now())) {
            momentoAlerta = Instant.now().plusSeconds(1);
        }

        taskScheduler.schedule(() -> {
            enviarNotificacaoPush(request.notificationSubscription(), request.title());
        }, momentoAlerta);
    }
    @Override
    public void enviarNotificacaoPush(PushSubscriptionDTO sub, String titulo) {
        // Em vez de escrever todo o JSON aqui de novo,
        // chamamos o outro método passando 'null' no lugar do ID.
        enviarNotificacaoPushComId(sub, titulo, null);
    }
    @Override
    public void confirmarAlerta(UUID id) {
        repository.findById(id).ifPresent(evento -> {
            evento.setAlertConfirmed(true);
            repository.save(evento);
            System.out.println("Evento " + id + " marcado como confirmado. Repetições encerradas.");
        });
    }

    @Override
    public List<EventsResponse> listAll() {
        // 1. Vai buscar todas as entidades ao banco de dados
        List<Evento> eventos = repository.findAll();

        // 2. Transforma a lista de entidades em lista de DTOs usando o mapper
        return eventos.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }


    private void agendarAlertaComRepeticao(UUID eventoId, EventRequest request) {
        LocalDateTime dataHoraEvento = LocalDateTime.of(request.referenceDate(), request.startTime());
        Instant momentoAlerta = dataHoraEvento.atZone(ZoneId.systemDefault()).toInstant();

        if (momentoAlerta.isBefore(Instant.now())) {
            momentoAlerta = Instant.now().plusSeconds(1);
        }

        taskScheduler.schedule(() -> dispararFluxoRepeticao(eventoId, request), momentoAlerta);
    }
    private void dispararFluxoRepeticao(UUID eventoId, EventRequest request) {
        repository.findById(eventoId).ifPresent(evento -> {

            // CORREÇÃO: alertConfirmed com C maiúsculo
            if (!evento.isAlertConfirmed()) {

                enviarNotificacaoPushComId(request.notificationSubscription(), request.title(), eventoId);

                System.out.println("Alerta enviado para o evento " + eventoId + ". Repetindo em 1 minuto...");

                taskScheduler.schedule(
                        () -> dispararFluxoRepeticao(eventoId, request),
                        Instant.now().plus(1, ChronoUnit.MINUTES)
                );
            } else {
                System.out.println("O utilizador confirmou o evento " + eventoId + ". Parando alertas.");
            }
        });
    }
    private void enviarNotificacaoPushComId(PushSubscriptionDTO sub, String titulo, UUID eventoId) {
        try {
            Map<String, Object> payloadMap = Map.of(
                    "title", "ALERTA: " + titulo,
                    "body", "Clique aqui para confirmar que viu este alerta!",
                    "url", "/Lista-agenda",
                    "eventoId", eventoId // ID crucial aqui
            );

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            notificationService.sendPushNotification(sub, jsonPayload);
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }
}
