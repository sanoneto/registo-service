package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.service.EventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Controller responsável pela gestão de eventos e integração com Google Calendar.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventoController {

    private final EventsService eventsService;

    @PostMapping("/eventos")
    public ResponseEntity<EventsResponse> criarEvento(
            @RequestBody EventRequest eventRequest,
            @RequestHeader(value = "Authorization", required = false) String authAppToken,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        log.info("Recebido pedido para criar evento. X-Google-Token presente: {}", googleToken != null);

        if (authAppToken == null) {
            log.warn("ALERTA: Header Authorization ausente. Verifique a Gateway ou o Interceptor do Frontend.");
        }

        EventsResponse response = eventsService.create(eventRequest, googleToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/eventos")
    public ResponseEntity<List<EventsResponse>> listarTodos() {
        return ResponseEntity.ok(eventsService.listAll());
    }

    @GetMapping("/eventos/{id}/confirmar-alerta")
    public ResponseEntity<Void> confirmarAlerta(@PathVariable UUID id) {
        log.info(">>> PROCESSANDO CONFIRMAÇÃO PARA ID: {}", id);

        String nomeEvento = eventsService.confirmarAlerta(id);

        // Encode para evitar que caracteres especiais ou espaços quebrem a URL de redirecionamento
        String nomeEncoded = URLEncoder.encode(nomeEvento, StandardCharsets.UTF_8);

        // Redireciona o utilizador para a landing page do React
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("https://www.sanoneto.com/alerta-confirmado?evento=" + nomeEncoded))
                .build();
    }

    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> eliminarEvento(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String systemToken,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        log.info("Eliminando evento ID: {}. System Token: {}, Google Token: {}",
                id, systemToken != null, googleToken != null);

        eventsService.deleteById(id, googleToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eventos/{id}")
    public ResponseEntity<EventsResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventsService.findById(id));
    }

    @PutMapping("/eventos/{id}")
    public ResponseEntity<EventsResponse> atualizarEvento(
            @PathVariable UUID id,
            @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventsService.update(id, request));
    }

    @GetMapping("/eventos/sync-from-google")
    public ResponseEntity<List<EventsResponse>> syncFromGoogle(
            @RequestHeader("X-Google-Token") String googleToken,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Iniciando sincronização Google para usuário: {}", userId);
        List<EventsResponse> novosEventos = eventsService.syncFromGoogle(googleToken, userId);
        return ResponseEntity.ok(novosEventos);
    }
}