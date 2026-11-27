package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.service.EventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventoController {

    private final EventsService eventsService;

    @Value("${app.urls.frontend-base-url}")
    private String frontendBaseUrl;

    @PostMapping("/eventos")
    public ResponseEntity<EventsResponse> criarEvento(
            @RequestBody EventRequest eventRequest,
            @RequestHeader(value = "Authorization", required = false) String authAppToken,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        log.info("Recebido pedido para criar evento. X-Google-Token presente: {}",
                googleToken != null && !googleToken.isBlank());

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
        String nomeEncoded = URLEncoder.encode(nomeEvento, StandardCharsets.UTF_8);

        String frontendLimpo = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendLimpo + "/alerta-confirmado?evento=" + nomeEncoded))
                .build();
    }

    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> eliminarEvento(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String systemToken,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        log.info("Eliminando evento ID: {}. System Token: {}, Google Token: {}",
                id, systemToken != null, googleToken != null && !googleToken.isBlank());

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
            @RequestBody EventRequest request,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        log.info("Atualizando evento ID: {}. X-Google-Token presente: {}",
                id, googleToken != null && !googleToken.isBlank());

        return ResponseEntity.ok(eventsService.update(id, request, googleToken));
    }

    @GetMapping("/eventos/sync-from-google")
    public ResponseEntity<List<EventsResponse>> syncFromGoogle(
            @RequestHeader("X-Google-Token") String googleToken,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            Authentication authentication
    ) {
        String resolvedUserId = (userId != null && !userId.isBlank())
                ? userId
                : (authentication != null ? authentication.getName() : null);

        log.info("Iniciando sincronização Google para utilizador: {}", resolvedUserId);
        List<EventsResponse> novosEventos = eventsService.syncFromGoogle(googleToken, resolvedUserId);
        return ResponseEntity.ok(novosEventos);
    }
}