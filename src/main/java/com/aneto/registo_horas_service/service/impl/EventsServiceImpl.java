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
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.EventDateTime;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final WebClient targetServiceWebClient;
    private final TelegramBotService telegramBotService;


    @Override
    public EventsResponse create(EventRequest request, String googleToken) {
        log.info("### CAMADA SERVICE: Iniciando criação do evento: {}", request.title());

        // 1. Guarda localmente primeiro
        Evento novoEvento = mapper.toEntity(request);

        // IMPORTANTE: Garante que o username vindo do request é guardado na entidade
        // para que as repetições futuras saibam para quem enviar.
        novoEvento.setUsername(request.username());
        novoEvento = repository.save(novoEvento);

        // 2. Envio imediato via Telegram se for Mobile
        if (request.isMobile()) {
            log.info("📱 Detetado Mobile no Service: Iniciando busca de Chat ID para {}", request.username());
            try {
                // Agora passamos o userName que vem no DTO do request
                enviarViaTelegram(request.title(), novoEvento.getId(), request.username());
            } catch (Exception e) {
                log.error("❌ Erro ao disparar Telegram: {}", e.getMessage());
            }
        }

        // 3. Alerta Push / Repetições
        if (request.sendAlert()) {
            log.info("📢 Agendando fluxo de alertas para o evento...");
            // Certifica-te que o agendarAlertaComRepeticao usa o userName da entidade
            agendarAlertaComRepeticao(novoEvento.getId(), request);
        }

        // 4. Sincronização Google Calendar (Código original mantido)
        if (googleToken != null && !googleToken.isEmpty()) {
            processarSincronizacaoGoogle(novoEvento, request, googleToken);
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

    private Calendar getCalendarService(String accessToken) throws Exception {
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(accessToken);
        return new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Registo-Horas")
                .build();
    }

    @Override
    public void confirmarAlerta(UUID id) {
        repository.findById(id).ifPresentOrElse(evento -> {
            evento.setAlertConfirmed(true);
            repository.save(evento);
            log.info("✅ Sucesso: Evento {} confirmado pelo utilizador {}.", id, evento.getUsername());
        }, () -> log.error("❌ Erro: Tentativa de confirmar evento inexistente ID: {}", id));
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
            momentoAlerta = Instant.now().plusSeconds(2);
        }

        taskScheduler.schedule(() -> {
            enviarNotificacaoPush(request.notificationSubscription(), request.title(), request.isMobile(), request.username());
        }, momentoAlerta);
    }

    @Override
    public void enviarNotificacaoPush(PushSubscriptionDTO sub, String titulo, boolean isMobile, String username) {
        // Agora passamos o username para o método que contém a lógica de decisão (Telegram vs WebPush)
        enviarNotificacaoPushComId(sub, titulo, null, isMobile, username);
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
        ZoneId zoneLisboa = ZoneId.of("Europe/Lisbon");
        LocalDateTime dataHora = LocalDateTime.of(request.referenceDate(), request.startTime());
        Instant momentoInicio = dataHora.atZone(zoneLisboa).toInstant();

        // Se o horário já passou, agenda para daqui a 5 segundos
        if (momentoInicio.isBefore(Instant.now())) {
            momentoInicio = Instant.now().plusSeconds(5);
        }

        taskScheduler.schedule(() -> dispararFluxoRepeticao(eventoId), momentoInicio);
    }

    private void dispararFluxoRepeticao(UUID eventoId) {
        repository.findById(eventoId).ifPresent(evento -> {
            if (!evento.isAlertConfirmed()) {
                log.info("🔔 Disparando repetição de alerta para: {}", evento.getTitle());

                enviarViaTelegram(evento.getTitle(), eventoId, evento.getUsername());

                // Reagenda para daqui a 1 minuto se não confirmar
                taskScheduler.schedule(
                        () -> dispararFluxoRepeticao(eventoId),
                        Instant.now().plus(1, ChronoUnit.MINUTES)
                );
            }
        });
    }

    private void enviarNotificacaoPushComId(PushSubscriptionDTO sub, String titulo, UUID eventoId, boolean isMobile, String username) {
        if (isMobile) {
            log.info("📱 Detetado Mobile: Enviando via Telegram para o evento {} (Utilizador: {})", eventoId, username);
            // Agora passamos o username para a função que usa o WebClient
            enviarViaTelegram(titulo, eventoId, username);
        } else {
            log.info("💻 Detetado Desktop: Enviando via Web Push para o evento {}", eventoId);
            enviarViaWebPush(sub, titulo, eventoId);
        }
    }

    // Se o seu TelegramBotService tiver o método execute() herdado da biblioteca

    private void enviarViaTelegram(String titulo, UUID eventoId, String username) {
        String dynamicChatId = buscarTelegramChatIdRemoto(username);
        if (dynamicChatId == null || dynamicChatId.isBlank()) return;
        try {
            // 1. Criar o Botão de Confirmação
            String urlConfirmar = "https://www.sanoneto.com/api/v1/eventos/" + eventoId + "/confirmar-alerta";

            InlineKeyboardButton botaoConfirmar = InlineKeyboardButton.builder()
                    .text("Confirmar ✅")
                    .url(urlConfirmar)
                    .build();

            // 2. Montar o Teclado (Keyboard)
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(botaoConfirmar))
                    .build();

            // 3. Montar a Mensagem Final
            SendMessage message = SendMessage.builder()
                    .chatId(dynamicChatId)
                    .text("🚨 *ALERTA TREG*\n\nEvento: " + titulo + "\n\n_Clique no botão abaixo para confirmar._")
                    .parseMode("Markdown")
                    .replyMarkup(keyboard) // Aqui entra o botão!
                    .build();

            // 4. Enviar usando o bot ativo
            telegramBotService.enviarMensagem(message);
        } catch (Exception e) {
            if (e.getMessage().contains("blocked")) {
                log.error("🚫 Bloqueio detectado. Cancelando repetições para {}", username);
                // Opcional: confirmar o alerta no banco apenas para parar as tentativas
            }
        }
    }

    private Map<String, Object> getStringObjectMap(String titulo, UUID eventoId, String chatId) {
        // so se aplica a produçao no ambiente
        String urlConfirmar = "https://www.sanoneto.com/api/v1/eventos/" + eventoId + "/confirmar-alerta";

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

    private String buscarTelegramChatIdRemoto(String username) {
        return targetServiceWebClient.get()
                .uri("/telegram-id/{username}", username)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.error("❌ Erro ao buscar ChatID remoto para {}: {}", username, e.getMessage());
                    // Retorna um Mono vazio, que o .block() converterá para null
                    return Mono.empty();
                })
                .block();
    }

    private void processarSincronizacaoGoogle(Evento novoEvento, EventRequest request, String googleToken) {
        try {
            Calendar service = getCalendarService(googleToken);
            com.google.api.services.calendar.model.Event gEvent = new com.google.api.services.calendar.model.Event()
                    .setSummary(request.title())
                    .setDescription(request.notes());

            DateTime start = new DateTime(request.referenceDate().toString() + "T" + request.startTime() + ":00Z");
            DateTime End = new DateTime(request.referenceDate().toString() + "T" + request.endDate() + ":00Z");
            gEvent.setStart(new EventDateTime().setDateTime(start));
            gEvent.setEnd(new EventDateTime().setDateTime(End));

            com.google.api.services.calendar.model.Event executed = service.events().insert("primary", gEvent).execute();
            novoEvento.setGoogleEventId(executed.getId());
            repository.save(novoEvento);
        } catch (Exception e) {
            log.error("Erro sincronização Google: {}", e.getMessage());
        }
    }
}

