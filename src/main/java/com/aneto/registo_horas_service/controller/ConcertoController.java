package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.ConcertoDTO;
import com.aneto.registo_horas_service.service.ConcertoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ConcertoController {

    private final ConcertoService concertoService;

    // O Spring injeta automaticamente o ConcertoServiceImpl aqui
    public ConcertoController(ConcertoService concertoService) {
        this.concertoService = concertoService;
    }

    @GetMapping("/concertos")
    public ResponseEntity<List<ConcertoDTO>> getConcertos() {
        return ResponseEntity.ok(concertoService.buscarConcertosDePortugal());
    }
}