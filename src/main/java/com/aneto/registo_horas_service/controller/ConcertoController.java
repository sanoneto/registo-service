package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.ConcertoDTO;
import com.aneto.registo_horas_service.service.ConcertoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConcertoController {

    private final ConcertoService concertoService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO')  or hasRole('USER') and #username == authentication.name)")
    @GetMapping("/concertos")
    public ResponseEntity<List<ConcertoDTO>> getConcertos() {
        return ResponseEntity.ok(concertoService.buscarConcertosDePortugal());
    }
}