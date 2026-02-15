package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.service.PlanoPagamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planosPagamento")
@RequiredArgsConstructor
public class PlanoPagamentoController {

    private final PlanoPagamentoService planoService;

    @GetMapping("/socio/{noSocio}")
    public ResponseEntity<PlanoPagamentoDTO> getPlanoAtivoBySocio(@PathVariable String noSocio) {
        return planoService.buscarPlanoAtivo(noSocio)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}