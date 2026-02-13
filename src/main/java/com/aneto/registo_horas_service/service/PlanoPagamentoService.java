package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.repository.PlanoPagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanoPagamentoService {


    private final PlanoPagamentoRepository repository;

    public Optional<PlanoPagamento> buscarPlanoAtivo(String noSocio) {
        // Lógica: Retorna o último plano registado para este sócio
        // Se usares saldo, podes filtrar por planos onde aulasPack > 0
        return repository.findFirstByNoSocioOrderByIdDesc(noSocio);
    }
}