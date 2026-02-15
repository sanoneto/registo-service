package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.PlanoPagamentoDTO;
import com.aneto.registo_horas_service.mapper.PlanoPagamentoMapper;
import com.aneto.registo_horas_service.models.Training.PlanoPagamento;
import com.aneto.registo_horas_service.repository.PlanoPagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanoPagamentoService {

    private final PlanoPagamentoRepository repository;

    private final PlanoPagamentoMapper mapper; // Injeta o mapper

    public Optional<PlanoPagamentoDTO> buscarPlanoAtivo(String noSocio) {
        return repository.findFirstByNoSocioOrderByIdDesc(noSocio)
                .map(mapper::toPlanoPagamentoDTO); // Usa o mapper
    }
}