package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.FootballData;
import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import com.aneto.registo_horas_service.mapper.FootballMapper;
import com.aneto.registo_horas_service.service.FootballService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class FootballServiceImpl implements FootballService {

    private final WebClient footballWebClient;
    private final FootballMapper footballMapper;

    // Construtor manual: agora o Spring vê o @Qualifier sem erro
    public FootballServiceImpl(
            @Qualifier("footballWebClient") WebClient webClient,
            FootballMapper footballMapper) {
        this.footballWebClient = webClient;
        this.footballMapper = footballMapper;
    }

    @Override
    public Mono<ListJogosResponse> buscarJogosParaReact(String dataInicio, String dataFim) {
        return this.footballWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/matches")
                        .queryParam("dateFrom", dataInicio)
                        .queryParam("dateTo", dataFim)
                        .build())
                .retrieve()
                .bodyToMono(FootballData.class)
                .map(footballMapper::toListJogosResponse);
    }

}