package com.aneto.registo_horas_service.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PlanoResponseDTO(
        UUID id,
        String nomeAluno,
        String objetivo,
        String especialista,
        String estadoPlano,
        String estadoPedido,
        String link,
        LocalDate dataCreate,
        LocalDate dataUpdate
) {
}