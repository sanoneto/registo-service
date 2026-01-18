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
import java.util.List;
import java.util.UUID;

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
        log.info("X-Google-Token: {}",googleToken);
        if (authAppToken == null) {
            // Isto ajudará a depurar: se cair aqui, a Gateway está a "comer" o seu token
            log.info("ALERTA: Header Authorization chegou nulo ao microserviço!");
        }
        return ResponseEntity.ok(eventsService.create(eventRequest, googleToken));
    }

    @GetMapping("/eventos")
    public List<EventsResponse> listarTodos() {
        return eventsService.listAll();
    }


    // Adiciona o CrossOrigin para a Gateway/React não bloquear
    // Aceita tanto GET (Telegram) como POST (Service Worker)
    @GetMapping("/eventos/{id}/confirmar-alerta")
    public ResponseEntity<Void> confirmarAlerta(@PathVariable UUID id) {
        log.info(">>> PROCESSANDO CONFIRMAÇÃO PARA ID: {}", id);

        // 1. Executa a lógica de parar o alerta no banco de dados
        eventsService.confirmarAlerta(id);

        // Podes criar uma rota no teu React chamada /sucesso ou mandar para a Home
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("https://www.sanoneto.com/Lista-agenda?confirmado=true"))
                .build();
    }

    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> eliminarEvento(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String systemToken,
            @RequestHeader(value = "X-Google-Token", required = false) String googleToken
    ) {
        // Log para debugar se os tokens estão a chegar
        log.info(">>> System Token presente: {}", systemToken != null);
        log.info(">>> Google Token presente: {}", googleToken != null);

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
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Google-Token") String googleToken) {

        // Chamamos o serviço para buscar no Google e salvar o que for novo
        List<EventsResponse> novosEventos = eventsService.syncFromGoogle(googleToken);
        return ResponseEntity.ok(novosEventos);
    }
}