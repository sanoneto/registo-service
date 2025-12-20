package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record DiaDeJogoResponse(
         String data,
         List<JogosResponse> jogos
) {
}
