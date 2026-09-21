package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ConcertoDTO;
import java.util.List;

public interface ConcertoService {
    List<ConcertoDTO> buscarConcertosDePortugal();
}