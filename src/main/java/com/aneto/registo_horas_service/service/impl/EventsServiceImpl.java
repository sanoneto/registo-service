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
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {
    private static final Logger log = LoggerFactory.getLogger(EventsServiceImpl.class);
    private final EventoRepository repository;
    private final EventsMapper mapper;
    private final TaskScheduler taskScheduler; // Adicionado final
    private final NotificationService notificationService; // Injetado aqui
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${telegram.botToken}")
    private String botToken;

    @Value("${telegram.chatId}")
    private String chatId;

    @Value("${telegram.telegramUrl}")
    private String telegramUrl;

    @Override
    public EventsResponse create(EventRequest request, String googleToken) {
        // 1. Guarda localmente primeiro
        Evento novoEvento = mapper.toEntity(request);
        novoEvento = repository.save(novoEvento);

        // 2. Alerta Push Local
        if (request.sendAlert() && request.notificationSubscription() != null) {
            agendarAlertaComRepeticao(novoEvento.getId(), request);
        }

        // 3. Google Calendar
        if (googleToken != null && !googleToken.isEmpty()) {
            try {
                Calendar service = getCalendarService(googleToken);

                com.google.api.services.calendar.model.Event googleEvent = new com.google.api.services.calendar.model.Event()
                        .setSummary(request.title())
                        .setDescription(request.notes() + "\nProjeto: " + request.project());

                // Start: ISO 8601
                DateTime startDateTime = new DateTime(request.referenceDate().toString() + "T" + request.startTime() + ":00Z");
                googleEvent.setStart(new EventDateTime().setDateTime(startDateTime));

                // End: +1 hora para evitar erro de evento sem duração
                DateTime endDateTime = new DateTime(startDateTime.getValue() + 3600000);
                googleEvent.setEnd(new EventDateTime().setDateTime(endDateTime));

                if (request.sendAlert()) {
                    EventReminder[] reminderOverrides = new EventReminder[]{
                            new EventReminder().setMethod("popup").setMinutes(10),
                            new EventReminder().setMethod("popup").setMinutes(0)
                    };
                    googleEvent.setReminders(new com.google.api.services.calendar.model.Event.Reminders()
                            .setUseDefault(false)
                            .setOverrides(Arrays.asList(reminderOverrides)));
                }

                // Executa e recupera o ID gerado pelo Google
                com.google.api.services.calendar.model.Event executedEvent = service.events().insert("primary", googleEvent).execute();

                // IMPORTANTE: Guarda o ID do Google na nossa entidade para evitar duplicatas no futuro
                novoEvento.setGoogleEventId(executedEvent.getId());
                repository.save(novoEvento);

                log.info("Sincronizado com Google Calendar. ID: {}", executedEvent.getId());

            } catch (Exception e) {
                log.error("Falha na sincronização Google: {}", e.getMessage());
            }
        }

        return mapper.toResponse(novoEvento);
    }

    @Override
    public List<EventsResponse> syncFromGoogle(String googleToken) {
        try {
            Calendar service = getCalendarService(googleToken);
            DateTime now = new DateTime(System.currentTimeMillis());

            com.google.api.services.calendar.model.Events events = service.events().list("primary")
                    .setTimeMin(now)
                    .setMaxResults(50)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<com.google.api.services.calendar.model.Event> items = events.getItems();
            List<Evento> eventosNovos = new java.util.ArrayList<>();

            for (com.google.api.services.calendar.model.Event gEvent : items) {
                // VERIFICAÇÃO POR ID (Resolve o erro do seu repositório)
                if (!repository.existsByGoogleEventId(gEvent.getId())) {
                    Evento novo = new Evento();
                    novo.setGoogleEventId(gEvent.getId());
                    novo.setTitle(gEvent.getSummary());
                    novo.setNotes(gEvent.getDescription() != null ? gEvent.getDescription() : "Importado do Google");

                    DateTime start = gEvent.getStart().getDateTime();
                    if (start != null) {
                        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(start.getValue()), ZoneId.systemDefault());
                        novo.setReferenceDate(ldt.toLocalDate());
                        novo.setStartTime(ldt.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
                    }
                    eventosNovos.add(repository.save(novo));
                }
            }
            return eventosNovos.stream().map(mapper::toResponse).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erro na sincronização: {}", e.getMessage());
            throw new RuntimeException("Falha ao ler agenda do Google");
        }
    }

    private Calendar getCalendarService(String accessToken) throws GeneralSecurityException, IOException {
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(accessToken);
        return new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Aneto-Registo-Horas")
                .build();
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
            enviarNotificacaoPush(request.notificationSubscription(), request.title(),request.isMobile());
        }, momentoAlerta);
    }

    @Override
    public void enviarNotificacaoPush(PushSubscriptionDTO sub, String titulo, boolean isMobile) {
        // Em vez de escrever todo o JSON aqui de novo,
        // chamamos o outro método passando 'null' no lugar do ID.
        enviarNotificacaoPushComId(sub, titulo, null,isMobile);
    }
    @Override
    @Transactional
    public void deleteById(UUID id, String googleToken) {
        repository.findById(id).ifPresentOrElse(
                evento -> {
                    // 1. Tenta apagar no Google Agenda apenas se tivermos o ID e o Token
                    if (evento.getGoogleEventId() != null && googleToken != null && !googleToken.isBlank()) {
                        try {
                            // Movemos o getCalendarService para dentro do try
                            Calendar service = getCalendarService(googleToken);
                            service.events().delete("primary", evento.getGoogleEventId()).execute();
                            log.info(">>> Evento removido do Google Agenda: {}", evento.getGoogleEventId());
                        } catch (Exception e) {
                            // Importante: Logar o erro mas permitir que o delete local continue
                            log.error(">>> Erro ao apagar no Google (O evento pode já não existir lá): {}", e.getMessage());
                        }
                    }

                    // 2. Apaga no seu banco de dados local
                    // Isso deve estar FORA do try do Google para garantir que o registro local suma
                    repository.delete(evento);
                    log.info(">>> Evento com ID {} eliminado localmente.", id);
                },
                () -> {
                    log.warn(">>> Tentativa de eliminar evento inexistente: {}", id);
                    // Opcional: lançar uma exceção específica para o Spring retornar 404 em vez de 500
                    throw new NoSuchElementException("Evento não encontrado com o ID: " + id);
                }
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
        return repository.findById(id).map(existente -> {
            boolean horarioMudou = !existente.getStartTime().equals(request.startTime()) ||
                    !existente.getReferenceDate().equals(request.referenceDate());

            mapper.updateEntityFromDto(request, existente);
            if (horarioMudou) existente.setAlertConfirmed(false);

            Evento salvo = repository.save(existente);
            if (request.sendAlert() && horarioMudou) agendarAlertaComRepeticao(salvo.getId(), request);

            return mapper.toResponse(salvo);
        }).orElseThrow();
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
        LocalDateTime dataHora = LocalDateTime.of(request.referenceDate(), request.startTime());
        Instant momento = dataHora.atZone(ZoneId.systemDefault()).toInstant();
        if (momento.isBefore(Instant.now())) momento = Instant.now().plusSeconds(2);

        taskScheduler.schedule(() -> dispararFluxoRepeticao(eventoId, request), momento);
    }

    private void dispararFluxoRepeticao(UUID eventoId, EventRequest request) {
        repository.findById(eventoId).ifPresent(evento -> {

            // CORREÇÃO: alertConfirmed com C maiúsculo
            if (!evento.isAlertConfirmed()) {

                enviarNotificacaoPushComId(request.notificationSubscription(), request.title(), eventoId, request.isMobile());

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

    private void enviarNotificacaoPushComId(PushSubscriptionDTO sub, String titulo, UUID eventoId, boolean isMobile) {
        if (isMobile) {
            log.info("📱 Detectado Mobile: Enviando via Telegram para o evento {}", eventoId);
            enviarViaTelegram(titulo, eventoId);
        } else {
            log.info("💻 Detectado Desktop: Enviando via Web Push para o evento {}", eventoId);
            enviarViaWebPush(sub, titulo, eventoId);
        }
    }

    private void enviarViaTelegram(String titulo, UUID eventoId) {
        try {
            String vTelegramUrl = telegramUrl + botToken + "/sendMessage";

            // URL que o botão vai chamar para confirmar e parar o loop no seu backend
            Map<String, Object> body = getStringObjectMap(titulo, eventoId, chatId);

            restTemplate.postForEntity(vTelegramUrl, body, String.class);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar Telegram: {}", e.getMessage());
        }
    }

    @NotNull
    private static Map<String, Object> getStringObjectMap(String titulo, UUID eventoId, String chatId) {
        String urlConfirmar = "https://treg-aneto.com/api/v1/eventos/" + eventoId + "/confirmar-alerta";

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", "🚨 *ALERTA TREG*\n\nEvento: " + titulo + "\n_Clique no botão abaixo para confirmar._",
                "parse_mode", "Markdown",
                "reply_markup", Map.of(
                        "inline_keyboard", List.of(
                                List.of(Map.of("text", "Confirmar ✅", "url", urlConfirmar))
                        )
                )
        );
        return body;
    }

    private void enviarViaWebPush(PushSubscriptionDTO sub, String titulo, UUID eventoId) {
        if (sub == null || sub.getEndpoint() == null) {
            log.warn("⚠️ Tentativa de Web Push sem subscrição válida.");
            return;
        }
        try {
            Map<String, Object> payloadMap = Map.of(
                    "title", "ALERTA: " + titulo,
                    "body", "Clique para confirmar!",
                    "url", "/Lista-agenda",
                    "eventoId", eventoId
            );

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            notificationService.sendPushNotification(sub, jsonPayload);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar Web Push: {}", e.getMessage());
        }
    }
}

