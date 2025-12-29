package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PlanoService {
    PlanoResponseDTO createPlano(PlanoRequestDTO request);
     PlanoResponseDTO getByPlanoById (UUID id);
    void deletePlano(UUID id);
    Page<PlanoResponseDTO> listAllOrName(String nome, Pageable pageable);
    Optional<PlanoResponseDTO> findAtivoAndConcluidoByUsername(String username);
    void updatePlano(String uuid, PlanoRequestDTO requestDTO);
}
