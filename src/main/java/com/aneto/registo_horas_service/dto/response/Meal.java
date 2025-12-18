package com.aneto.registo_horas_service.dto.response;

import java.util.List;

public record Meal(
        String time,
        String description,
        List<String> ingredients
) {
}
