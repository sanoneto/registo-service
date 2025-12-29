package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.FootballData;
import com.aneto.registo_horas_service.dto.response.ListJogosResponse;

import java.util.Optional;

public interface DashboardService {
    ListJogosResponse getListJogo(String username);

    //ListJogosResponse createtListJogo(String username);

    void saveToS3(String key, ListJogosResponse plan);

    Optional<ListJogosResponse> loadFromS3jogos(String key);
}
