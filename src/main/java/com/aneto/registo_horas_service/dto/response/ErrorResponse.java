package com.aneto.registo_horas_service.dto.response;

public record ErrorResponse(
        String message,
        int status
) {
}