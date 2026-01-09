package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.EventRequest;
import com.aneto.registo_horas_service.dto.response.EventsResponse;
import com.aneto.registo_horas_service.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/eventos")
@RequiredArgsConstructor
public class EventoController {
    private final EventsService eventsService;

    @PostMapping
    public ResponseEntity<EventsResponse> criarEvento(@RequestBody EventRequest eventRequest) {
        // O Spring converterá automaticamente o JSON do React em objeto Java
        EventsResponse salvo = eventsService.criarEvento(eventRequest);
        return ResponseEntity.ok(salvo);
    }

    @GetMapping
    public List<EventsResponse> listarTodos() {
        return eventsService.listAll();
    }
}