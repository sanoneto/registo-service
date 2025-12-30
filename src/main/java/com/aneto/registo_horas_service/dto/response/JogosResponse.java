package com.aneto.registo_horas_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JogosResponse(
        String id,
        String liga,
        @JsonProperty("equipa_casa")
        String equipaCasa,
        @JsonProperty("equipa_fora")
        String equipaFora,
        String hora,
        String canal,
        String iconHome,
        String iconAway,
        String scoreHome,
        String scoreAway
) {
}
