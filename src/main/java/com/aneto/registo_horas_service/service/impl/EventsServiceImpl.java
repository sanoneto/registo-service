package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.request.PushSubscriptionDTO;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.mapper.EventsMapper;
import com.aneto.registo_horas_service.models.Evento;
import com.aneto.registo_horas_service.repository.EventoRepository;
import com.aneto.registo_horas_service.service.EventsService;
import com.aneto.registo_horas_service.service.NotificationService;
import com.aneto.registo_horas_service.service.TelegramBotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private static final Logger log = LoggerFactory.getLogger(EventsServiceImpl.class);
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");
    private static final int MAX_TENTATIVAS = 10;

    private final EventoRepository eventRepository;
    private final EventsMapper mapper;
    private final TaskScheduler taskScheduler;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WebClient targetServiceWebClient;
    private final TelegramBotService telegramBotService;

    @Value("${app.urls.api-base-url}")
    private String apiBaseUrl;

    @Override
    public EventsResponse create(EventRequest request, String googleToken) {
        log.info("### CAMADA SERVICE: Iniciando criação do evento: {}", request.title());

        Evento novoEvento = mapper.toEntity(request);
        novoEvento.setUsername(request.username());
        novoEvento = eventRepository.save(novoEvento);

        // 1. Alerta insistente Telegram apenas na hora do evento (se sendAlert = true)
        if (request.sendAlert()) {
            log.info("📢 Agendando alerta com confirmação para a hora do evento...");
            agendarAlertaComRepeticao(novoEvento.getId(), request);
        }

        // 2. Sincronização automática com Google Calendar + Lembretes nativos no telemóvel
        if (googleToken != null && !googleToken.isBlank()) {
            sincronizarEventoComGoogle(novoEvento, googleToken);
        }

        return toEnrichedResponse(novoEvento);
    }

    @Override
    public EventsResponse update(UUID id, EventRequest request, String googleToken) {
        return eventRepository.findById(id).map(existente -> {
            boolean horarioMudou = !existente.getStartTime().equals(request.startTime()) ||
                    !existente.getReferenceDate().equals(request.referenceDate());

            mapper.updateEntityFromDto(request, existente);
            if (horarioMudou) {
                existente.setAlertConfirmed(false);
            }

            Evento salvo = eventRepository.save(existente);

            if (request.sendAlert() && horarioMudou) {
                agendarAlertaComRepeticao(salvo.getId(), request);
            }

            if (googleToken != null && !googleToken.isBlank()) {
                sincronizarEventoComGoogle(salvo, googleToken);
            }

            return toEnrichedResponse(salvo);
        }).orElseThrow(() -> new NoSuchElementException("Evento não encontrado com o ID: " + id));
    }

    @Override
    public List<EventsResponse> syncFromGoogle(String googleToken, String userId) {
        try {
            Calendar service = getCalendarService(googleToken);

            // PASSO A: Enviar para o Google Calendar quaisquer eventos locais futuros que ainda não foram sincronizados
            List<Evento> pendentesEnvio = eventRepository.findAll().stream()
                    .filter(e -> e.getGoogleEventId() == null || e.getGoogleEventId().isBlank())
                    .filter(e -> userId == null || userId.isBlank() || userId.equalsIgnoreCase(e.getUsername()))
                    .filter(e -> e.getReferenceDate() != null && !e.getReferenceDate().isBefore(LocalDate.now(ZONE_LISBOA)))
                    .collect(Collectors.toList());

            for (Evento localPendente : pendentesEnvio) {
                log.info("⬆️ Enviando evento local '{}' para o Google Calendar...", localPendente.getTitle());
                sincronizarEventoComGoogle(localPendente, googleToken);
            }

            // PASSO B: Importar novos eventos do Google Calendar (próximos 90 dias)
            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime limit = new DateTime(System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000));

            CalendarList calendarList = service.calendarList().list().execute();
            List<Evento> todosEventosNovos = new ArrayList<>();

            for (CalendarListEntry entry : calendarList.getItems()) {
                if ("reader".equals(entry.getAccessRole()) && entry.getId().contains("#holiday")) {
                    continue;
                }

                com.google.api.services.calendar.model.Events events = service.events().list(entry.getId())
                        .setTimeMin(now)
                        .setTimeMax(limit)
                        .setSingleEvents(true)
                        .execute();

                if (events.getItems() == null) continue;

                for (Event gEvent : events.getItems()) {
                    if (gEvent.getId() == null || gEvent.getSummary() == null) continue;

                    if (!eventRepository.existsByGoogleEventIdAndUsername(gEvent.getId(), userId)) {
                        Evento novo = mapGoogleEventToEntity(gEvent, userId);
                        if (novo != null) {
                            todosEventosNovos.add(eventRepository.save(novo));
                        }
                    }
                }
            }

            return todosEventosNovos.stream()
                    .map(this::toEnrichedResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erro na sincronização completa com Google Calendar: ", e);
            throw new RuntimeException("Falha ao sincronizar com Google Calendar: " + e.getMessage());
        }
    }

    private void sincronizarEventoComGoogle(Evento evento, String googleToken) {
        try {
            Calendar service = getCalendarService(googleToken);
            Event gEvent = buildGoogleEvent(evento);

            if (evento.getGoogleEventId() != null && !evento.getGoogleEventId().isBlank()) {
                try {
                    Event updated = service.events()
                            .update("primary", evento.getGoogleEventId(), gEvent)
                            .execute();
                    evento.setGoogleEventId(updated.getId());
                    eventRepository.save(evento);
                    log.info("✅ Evento '{}' atualizado no Google Calendar (ID: {})", evento.getTitle(), updated.getId());
                    return;
                } catch (Exception ex) {
                    log.warn("⚠️ Evento não encontrado no Google para update, a criar novo: {}", ex.getMessage());
                }
            }

            Event inserted = service.events().insert("primary", gEvent).execute();
            evento.setGoogleEventId(inserted.getId());
            eventRepository.save(evento);
            log.info("✅ Evento '{}' criado no Google Calendar com alertas push (ID: {})", evento.getTitle(), inserted.getId());
        } catch (Exception e) {
            log.error("❌ Erro na sincronização com Google Calendar: {}", e.getMessage(), e);
        }
    }

    private Event buildGoogleEvent(Evento evento) {
        String descricao = (evento.getNotes() != null && !evento.getNotes().isBlank())
                ? evento.getNotes()
                : "";
        if (evento.getProject() != null && !evento.getProject().isBlank()) {
            descricao = "Projeto: " + evento.getProject() + (descricao.isEmpty() ? "" : "\n" + descricao);
        }

        Event gEvent = new Event()
                .setSummary(evento.getTitle())
                .setDescription(descricao);

        LocalDate dataInicio = evento.getReferenceDate();
        LocalTime horaInicio = evento.getStartTime() != null ? evento.getStartTime() : LocalTime.of(9, 0);
        LocalTime horaFim = (evento.getEndTime() != null && evento.getEndTime().isAfter(horaInicio))
                ? evento.getEndTime()
                : horaInicio.plusHours(1);

        ZonedDateTime zdtStart = ZonedDateTime.of(dataInicio, horaInicio, ZONE_LISBOA);
        ZonedDateTime zdtEnd = ZonedDateTime.of(dataInicio, horaFim, ZONE_LISBOA);

        String startRfc3339 = zdtStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endRfc3339 = zdtEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        gEvent.setStart(new EventDateTime()
                .setDateTime(new DateTime(startRfc3339))
                .setTimeZone("Europe/Lisbon"));

        gEvent.setEnd(new EventDateTime()
                .setDateTime(new DateTime(endRfc3339))
                .setTimeZone("Europe/Lisbon"));

        EventReminder[] reminderOverrides = new EventReminder[]{
                new EventReminder().setMethod("popup").setMinutes(30),
                new EventReminder().setMethod("popup").setMinutes(10)
        };
        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));
        gEvent.setReminders(reminders);

        return gEvent;
    }

    private EventsResponse toEnrichedResponse(Evento evento) {
        boolean isSynced = evento.getGoogleEventId() != null && !evento.getGoogleEventId().isBlank();
        return new EventsResponse(
                evento.getId(),
                evento.getUsername(),
                evento.getTitle(),
                evento.getProject(),
                evento.getReferenceDate(),
                evento.getStartTime(),
                evento.getEndTime(),
                evento.getNotes(),
                evento.isSendAlert(),
                isSynced
        );
    }

    private Calendar getCalendarService(String accessToken) {
        AccessToken token = new AccessToken(accessToken, null);
        GoogleCredentials credentials = GoogleCredentials.create(token);

        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("ProAct-Agenda")
                .build();
    }

    @Override
    @Transactional
    public String confirmarAlerta(UUID id) {
        Evento evento = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));

        if (!evento.isAlertConfirmed()) {
            evento.setAlertConfirmed(true);
            eventRepository.save(evento);
            log.info("Alerta confirmado com sucesso para o evento: {}", id);
        } else {
            log.info("O alerta para o evento {} já havia sido confirmado anteriormente.", id);
        }
        return evento.getTitle();
    }

    @Override
    public void agendarAlerta(EventRequest request) {
        LocalDateTime dataHoraEvento = LocalDateTime.of(request.referenceDate(), request.startTime());

        Instant momentoAlerta = dataHoraEvento
                .atZone(ZONE_LISBOA)
                .toInstant();

        if (momentoAlerta.isBefore(Instant.now())) {
            momentoAlerta = Instant.now().plusSeconds(2);
        }

        taskScheduler.schedule(
                () -> enviarNotificacaoPush(request.notificationSubscription(), request.title(), request.isMobile(), request.username()),
                momentoAlerta
        );
    }

    @Override
    public void enviarNotificacaoPush(PushSubscriptionDTO sub, String titulo, boolean isMobile, String username) {
        enviarNotificacaoPushComId(sub, titulo, null, isMobile, username);
    }

    @Override
    @Transactional
    public void deleteById(UUID id, String googleToken) {
        eventRepository.findById(id).ifPresentOrElse(
                evento -> {
                    if (evento.getGoogleEventId() != null && googleToken != null && !googleToken.isBlank()) {
                        try {
                            Calendar service = getCalendarService(googleToken);
                            service.events().delete("primary", evento.getGoogleEventId()).execute();
                            log.info(">>> Evento removido do Google Agenda: {}", evento.getGoogleEventId());
                        } catch (Exception e) {
                            log.error(">>> Erro ao apagar no Google: {}", e.getMessage());
                        }
                    }

                    eventRepository.delete(evento);
                    log.info(">>> Evento com ID {} eliminado localmente.", id);
                },
                () -> {
                    log.warn(">>> Tentativa de eliminar evento inexistente: {}", id);
                    throw new NoSuchElementException("Evento não encontrado com o ID: " + id);
                }
        );
    }

    @Override
    public EventsResponse findById(UUID id) {
        return eventRepository.findById(id)
                .map(this::toEnrichedResponse)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com o ID: " + id));
    }

    @Override
    public List<EventsResponse> listAll() {
        return eventRepository.findAll().stream()
                .map(this::toEnrichedResponse)
                .collect(Collectors.toList());
    }

    private void agendarAlertaComRepeticao(UUID eventoId, EventRequest request) {
        LocalDateTime dataHora = LocalDateTime.of(request.referenceDate(), request.startTime());
        Instant momentoInicio = dataHora.atZone(ZONE_LISBOA).toInstant();

        if (momentoInicio.isBefore(Instant.now())) {
            momentoInicio = Instant.now().plusSeconds(5);
        }

        taskScheduler.schedule(() -> dispararFluxoRepeticao(eventoId), momentoInicio);
    }

    private void dispararFluxoRepeticao(UUID eventoId) {
        dispararFluxoRepeticao(eventoId, 1);
    }

    private void dispararFluxoRepeticao(UUID eventoId, int tentativa) {
        eventRepository.findById(eventoId).ifPresent(evento -> {
            if (evento.isAlertConfirmed()) return;

            if (tentativa > MAX_TENTATIVAS) {
                log.warn("⏹️ Alerta {} sem confirmação após {} tentativas. A parar.", eventoId, MAX_TENTATIVAS);
                return;
            }

            log.info("🔔 Repetição {}/{} para: {}", tentativa, MAX_TENTATIVAS, evento.getTitle());

            boolean enviado = enviarViaTelegram(evento.getTitle(), eventoId, evento.getUsername());
            if (!enviado) {
                log.warn("⏹️ Não foi possível enviar o alerta {}. A parar repetições.", eventoId);
                return;
            }

            taskScheduler.schedule(
                    () -> dispararFluxoRepeticao(eventoId, tentativa + 1),
                    Instant.now().plus(1, ChronoUnit.MINUTES));
        });
    }

    private void enviarNotificacaoPushComId(PushSubscriptionDTO sub, String titulo, UUID eventoId, boolean isMobile, String username) {
        if (isMobile) {
            enviarViaTelegram(titulo, eventoId, username);
        } else {
            enviarViaWebPush(sub, titulo, eventoId);
        }
    }

    private boolean enviarViaTelegram(String titulo, UUID eventoId, String username) {
        String dynamicChatId = buscarTelegramChatIdRemoto(username);
        if (dynamicChatId == null || dynamicChatId.isBlank()) return false;
        try {
            String baseUrlLimpa = (apiBaseUrl != null && apiBaseUrl.endsWith("/"))
                    ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1)
                    : apiBaseUrl;
            String urlConfirmacao = baseUrlLimpa + "/api/v1/eventos/" + eventoId + "/confirmar-alerta";

            boolean isLocalhost = baseUrlLimpa != null &&
                    (baseUrlLimpa.contains("://localhost") || baseUrlLimpa.contains("://127.0.0.1"));

            if (isLocalhost) {
                String mensagemLocal = "🚨 *ALERTA PROACT*\n\n" +
                        "Evento: " + titulo + "\n\n" +
                        "_Clique no link abaixo para confirmar (ambiente local):_\n" +
                        urlConfirmacao;

                return telegramBotService.enviarMensagemSimples(dynamicChatId, mensagemLocal);
            }

            InlineKeyboardButton botaoConfirmar = InlineKeyboardButton.builder()
                    .text("Confirmar ✅")
                    .url(urlConfirmacao)
                    .build();

            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(botaoConfirmar))
                    .build();

            SendMessage message = SendMessage.builder()
                    .chatId(dynamicChatId)
                    .text("🚨 *ALERTA PROACT*\n\nEvento: " + titulo + "\n\n_Clique no botão abaixo para confirmar._")
                    .parseMode("Markdown")
                    .replyMarkup(keyboard)
                    .build();

            telegramBotService.enviarMensagem(message);
            return true;
        } catch (Exception e) {
            log.error("❌ Falha ao enviar Telegram para {}: {}", username, e.getMessage());
            return false;
        }
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

    private String buscarTelegramChatIdRemoto(String username) {
        return targetServiceWebClient.get()
                .uri("/api/auth/telegram-id/{username}", username)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    Throwable causa = (e.getCause() != null) ? e.getCause() : e;
                    log.error("❌ Erro ao buscar ChatID remoto para {}: {} -> {} ({})",
                            username,
                            e.getClass().getSimpleName(),
                            causa.getClass().getSimpleName(),
                            causa.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    private Evento mapGoogleEventToEntity(Event gEvent, String userId) {
        Evento novo = new Evento();
        novo.setGoogleEventId(gEvent.getId());
        novo.setTitle(gEvent.getSummary());
        novo.setUsername(userId);
        novo.setNotes(gEvent.getDescription() != null ? gEvent.getDescription() : "Importado do Google");

        if (gEvent.getStart().getDateTime() != null) {
            LocalDateTime ldt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(gEvent.getStart().getDateTime().getValue()),
                    ZONE_LISBOA
            );
            novo.setReferenceDate(ldt.toLocalDate());
            novo.setStartTime(ldt.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        } else if (gEvent.getStart().getDate() != null) {
            LocalDate date = LocalDate.parse(gEvent.getStart().getDate().toStringRfc3339().substring(0, 10));
            novo.setReferenceDate(date);
            novo.setStartTime(LocalTime.MIDNIGHT);
        } else {
            return null;
        }

        return novo;
    }

    @PostConstruct
    public void recuperarAlertasPendentes() {
        log.info("### 🔄 Verificando alertas pendentes para recuperação...");

        LocalDate hoje = LocalDate.now(ZONE_LISBOA);
        LocalDateTime agora = LocalDateTime.now(ZONE_LISBOA);

        List<Evento> pendentes = eventRepository.findPendentesParaNotificar(hoje);

        log.info("### 🔍 Encontrados {} eventos com alertas ativos no banco.", pendentes.size());

        pendentes.forEach(evento -> {
            LocalDateTime dataHoraEvento = LocalDateTime.of(evento.getReferenceDate(), evento.getStartTime());
            if (dataHoraEvento.isBefore(agora) || dataHoraEvento.isEqual(agora)) {
                log.info("### 🚀 Retomando fluxo de repetição para: {}", evento.getTitle());
                dispararFluxoRepeticao(evento.getId());
            }
        });
    }
}