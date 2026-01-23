package com.aneto.registo_horas_service.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true) // Regra de Ouro 1: Não crashar com chaves novas
public record Meal(
        @JsonAlias({"mealTime", "hour", "horario"}) // Regra de Ouro 2: Aceitar variações da IA
        String time,

        @JsonAlias({"meal", "name", "label"})
        String description,

        List<String> ingredients
) {
}