package com.aneto.registo_horas_service.dto.response;

public record PerfilResponse(
        String username,
        String project_name,
        String email,
        Double required_hours,
        Double Total_horas_trabalhadas,
        String profile_picture_url,
        String public_id,
        String telegram_chat_id
) {
}
