package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.service.PlanoPagamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planosPagamento")
@RequiredArgsConstructor
public class PlanoPagamentoController {

    private final PlanoPagamentoService planoService;

    @GetMapping("/socio/{noSocio}")
    public ResponseEntity<PlanoPagamento> getPlanoAtivoBySocio(@PathVariable String noSocio) {
        // Procura o plano mais recente que ainda tenha aulas (ou o último criado)
        return planoService.buscarPlanoAtivo(noSocio)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}