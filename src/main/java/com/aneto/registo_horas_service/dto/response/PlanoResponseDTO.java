package com.aneto.registo_horas_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.With;

import java.time.LocalDate;
import java.util.UUID;
@With
public record PlanoResponseDTO(
        UUID id,
        String nomeAluno,
        String recommended,
        String objetivo,
        String especialista,
        String estadoPlano,
        String estadoPedido,
        String link,
        LocalDate dataCreate,
        LocalDate dataUpdate
) {
}