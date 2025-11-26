package com.aneto.registo_horas_service.dto.response;

public record PerfilResponse(
        String username,
        String email,
        Double required_hours,
        Double Total_horas_trabalhadas
) {
}
