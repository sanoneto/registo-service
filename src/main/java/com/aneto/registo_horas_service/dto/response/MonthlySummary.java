package com.aneto.registo_horas_service.dto.response;

public record MonthlySummary(
        String mes_e_ano, // Ex: "2024-10"
        double total_horas_trabalhadas
) {
}
