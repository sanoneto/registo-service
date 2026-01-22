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
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
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
    private final EventoRepository eventRepository;
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
        novoEvento = eventRepository.save(novoEvento);

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
    public List<EventsResponse> syncFromGoogle(String googleToken, String userId) {
        try {
            Calendar service = getCalendarService(googleToken);

            // 1. Limitar a janela de tempo (ex: hoje até daqui a 3 meses)
            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime limit = new DateTime(System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000));

            // 2. Obter a lista de todos os calendários visíveis
            CalendarList calendarList = service.calendarList().list().execute();
            List<Evento> todosEventosNovos = new java.util.ArrayList<>();

            for (CalendarListEntry entry : calendarList.getItems()) {
                // Ignorar calendários de feriados se desejar, ou filtrar por ID
                if ("reader".equals(entry.getAccessRole()) && entry.getId().contains("#holiday")) {
                    log.info("Ignorando calendário de feriados: {}", entry.getSummary());
                    continue;
                }
                log.info("Sincronizando calendário: {}", entry.getSummary());

                com.google.api.services.calendar.model.Events events = service.events().list(entry.getId())
                        .setTimeMin(now)
                        .setTimeMax(limit) // 👈 Essencial para evitar aniversários repetidos até 2036
                        .setSingleEvents(true)
                        .execute();

                for (com.google.api.services.calendar.model.Event gEvent : events.getItems()) {
                    // Verificação de duplicados por ID de Evento E Utilizador
                    if (!eventRepository.existsByGoogleEventIdAndUsername(gEvent.getId(), userId)) {
                        Evento novo = mapGoogleEventToEntity(gEvent, userId);
                        if (novo != null) {
                            todosEventosNovos.add(eventRepository.save(novo));
                        }
                    }
                }
            }
            return todosEventosNovos.stream().map(mapper::toResponse).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erro na sincronização completa: ", e);
            throw new RuntimeException("Falha ao ler agendas do Google");
        }
    }

    private Calendar getCalendarService(String accessToken) {
        // Usamos uma subclasse que apenas retorna o token sem tentar renová-lo
        AccessToken token = new AccessToken(accessToken, null);
        GoogleCredentials credentials = GoogleCredentials.create(token);

        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Registo-Horas")
                .build();
    }

    @Override
    @Transactional
    public String confirmarAlerta(UUID id) {
        // 1. Procura o evento no banco
        Evento evento = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));

        // 2. Verifica se o alerta já foi confirmado anteriormente
        // Se alertConfirmed for false, significa que o alerta ainda está "ativo" ou pendente
        if (!evento.isAlertConfirmed()) {
            evento.setAlertConfirmed(true); // Marca como confirmado (para parar de enviar notificações)
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
        eventRepository.findById(id).ifPresentOrElse(
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
                    eventRepository.delete(evento);
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
        return eventRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com o ID: " + id));
    }

    @Override
    public EventsResponse update(UUID id, EventRequest request) {
        return eventRepository.findById(id).map(existente -> {
            boolean horarioMudou = !existente.getStartTime().equals(request.startTime()) ||
                    !existente.getReferenceDate().equals(request.referenceDate());

            mapper.updateEntityFromDto(request, existente);
            if (horarioMudou) existente.setAlertConfirmed(false);

            Evento salvo = eventRepository.save(existente);
            if (request.sendAlert() && horarioMudou) agendarAlertaComRepeticao(salvo.getId(), request);

            return mapper.toResponse(salvo);
        }).orElseThrow();
    }

    @Override
    public List<EventsResponse> listAll() {
        // 1. Vai buscar todas as entidades ao banco de dados
        List<Evento> eventos = eventRepository.findAll();

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
        eventRepository.findById(eventoId).ifPresent(evento -> {
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

            // 1. Defina o formatador padrão RFC3339 que o Google exige
            DateTimeFormatter rfc3339Formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

            // 2. Combine data e hora (Assumindo que são LocalDate e LocalTime)
            OffsetDateTime startIso = request.referenceDate()
                    .atTime(request.startTime())
                    .atOffset(ZoneOffset.UTC);

            OffsetDateTime endIso = request.referenceDate()
                    .atTime(request.endTime())
                    .atOffset(ZoneOffset.UTC);

            // 3. Formate explicitamente para String antes de passar para o DateTime do Google
            gEvent.setStart(new EventDateTime().setDateTime(new DateTime(startIso.format(rfc3339Formatter))));
            gEvent.setEnd(new EventDateTime().setDateTime(new DateTime(endIso.format(rfc3339Formatter))));

            com.google.api.services.calendar.model.Event executed = service.events().insert("primary", gEvent).execute();
            novoEvento.setGoogleEventId(executed.getId());
            eventRepository.save(novoEvento);
        } catch (Exception e) {
            log.error("Erro sincronização Google: {}", e.getMessage());
        }
    }

    private Evento mapGoogleEventToEntity(com.google.api.services.calendar.model.Event gEvent, String userId) {
        Evento novo = new Evento();
        novo.setGoogleEventId(gEvent.getId());
        novo.setTitle(gEvent.getSummary());
        novo.setUsername(userId);
        novo.setNotes(gEvent.getDescription() != null ? gEvent.getDescription() : "Importado do Google");

        // Tratar data de eventos com hora (DateTime)
        if (gEvent.getStart().getDateTime() != null) {
            LocalDateTime ldt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(gEvent.getStart().getDateTime().getValue()),
                    ZoneId.systemDefault()
            );
            novo.setReferenceDate(ldt.toLocalDate());
            novo.setStartTime(ldt.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        }
        // Tratar eventos de dia inteiro (Date) - Resolve o erro dos Aniversários
        else if (gEvent.getStart().getDate() != null) {
            LocalDate date = LocalDate.parse(gEvent.getStart().getDate().toStringRfc3339().substring(0, 10));
            novo.setReferenceDate(date);
            novo.setStartTime(LocalTime.MIDNIGHT);
        } else {
            return null; // Caso não tenha data nenhuma (inválido)
        }

        return novo;
    }

    // Adicione esta anotação para correr ao iniciar
    @PostConstruct
    public void recuperarAlertasPendentes() {
        log.info("### 🔄 Verificando alertas pendentes para recuperação...");

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        // 1. Usa o método otimizado do repositório
        List<Evento> pendentes = eventRepository.findPendentesParaNotificar(hoje);

        log.info("### 🔍 Encontrados {} eventos com alertas ativos no banco.", pendentes.size());

        pendentes.forEach(evento -> {
            LocalDateTime dataHoraEvento = LocalDateTime.of(evento.getReferenceDate(), evento.getStartTime());

            // 2. Só reinicia o fluxo se a hora do evento já passou ou é exatamente agora
            if (dataHoraEvento.isBefore(agora) || dataHoraEvento.isEqual(agora)) {
                log.info("### 🚀 Retomando fluxo de repetição para: {}", evento.getTitle());
                dispararFluxoRepeticao(evento.getId());
            }
        });
    }
}

