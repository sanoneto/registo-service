package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import reactor.core.publisher.Mono;

public interface FootballService {
    // Mono<FootballData> buscarPartidasPorPeriodo(String dataInicio, String dataFim);
    Mono<ListJogosResponse> buscarJogosParaReact(String dataInicio, String dataFim);
}