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
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {
    private static final Logger log = LoggerFactory.getLogger(EventsServiceImpl.class);
    private final EventoRepository repository;
    private final EventsMapper mapper;
    private final TaskScheduler taskScheduler; // Adicionado final
    private final NotificationService notificationService; // Injetado aqui
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public EventsResponse create(EventRequest request, String googleToken) {
        // 1. Guarda na base de dados local (comportamento padrão)
        Evento novoEvento = mapper.toEntity(request);
        novoEvento = repository.save(novoEvento);

        // 2. Lógica de Push Notification (as funções que você postou)
        if (request.sendAlert() && request.notificationSubscription() != null) {
            agendarAlertaComRepeticao(novoEvento.getId(), request);
        }

        // 3. NOVO: Sincronização com Google Calendar
        if (googleToken != null && !googleToken.isEmpty()) {
            try {
                Calendar service = getCalendarService(googleToken);

                // Criar o objeto de evento da Google
                com.google.api.services.calendar.model.Event googleEvent = new com.google.api.services.calendar.model.Event()
                        .setSummary(request.title())
                        .setDescription(request.notes() + "\nProjeto: " + request.project());

                // Configurar horários
                DateTime startDateTime = new DateTime(request.referenceDate().toString() + "T" + request.startTime() + ":00Z");
                googleEvent.setStart(new EventDateTime().setDateTime(startDateTime));

                // Definir fim (ex: 1h depois se não houver endTime)
                googleEvent.setEnd(new EventDateTime().setDateTime(startDateTime));

                // Inserir na agenda principal do utilizador
                service.events().insert("primary", googleEvent).execute();
                log.info("Evento sincronizado com Google Calendar para o utilizador {}", request.username());

            } catch (Exception e) {
                log.error("Falha na sincronização Google: {}", e.getMessage());
                // Não travamos o processo se o Google falhar, o evento local já existe
            }
        }

        return mapper.toResponse(novoEvento);
    }
    private Calendar getCalendarService(String accessToken) throws GeneralSecurityException, IOException {
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Aneto-Registo-Horas") // Nome da sua app
                .build();
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
        log.info("Evento {} marcado como confirmado. Repetições encerradas.", id);
    });
}

@Override
public void deleteById(UUID id) {
    repository.findById(id)
            .ifPresentOrElse(
                    evento -> {
                        repository.delete(evento);
                        log.info(">>> Evento com ID {} foi eliminado com sucesso.", id);
                    },
                    () -> log.info(">>> Tentativa de eliminar evento inexistente: {}", id)
            );
}

@Override
public EventsResponse findById(UUID id) {
    return repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new RuntimeException("Evento não encontrado com o ID: " + id));
}

@Override
public EventsResponse update(UUID id, EventRequest request) {
    return repository.findById(id).map(eventoExistente -> {

        // 1. Detectar mudança de horário ANTES de mapear os novos dados
        boolean horarioMudou = !eventoExistente.getStartTime().equals(request.startTime()) ||
                !eventoExistente.getReferenceDate().equals(request.referenceDate());

        // 2. Mapeamento Automático (Substitui todos aqueles setters manuais)
        mapper.updateEntityFromDto(request, eventoExistente);

        // 3. Lógica de Alerta: Se mudou o horário, resetamos a confirmação
        if (horarioMudou) {
            log.info("Horário alterado para o evento {}. Reiniciando ciclo de alertas.", id);
            eventoExistente.setAlertConfirmed(false);
        }

        Evento atualizado = repository.save(eventoExistente);

        // 4. Reagendar se necessário
        if (request.sendAlert() && request.notificationSubscription() != null && horarioMudou) {
            agendarAlertaComRepeticao(atualizado.getId(), request);
        }
        return mapper.toResponse(atualizado);
    }).orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));
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

            log.info("Alerta enviado para o evento {}. Repetindo em 1 minuto...", eventoId);

            taskScheduler.schedule(
                    () -> dispararFluxoRepeticao(eventoId, request),
                    Instant.now().plus(1, ChronoUnit.MINUTES)
            );
        } else {
            log.info("O utilizador confirmou o evento {}. Parando alertas.", eventoId);
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
