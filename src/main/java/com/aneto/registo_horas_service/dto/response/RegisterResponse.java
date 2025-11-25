package com.aneto.registo_horas_service.dto.response;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RegisterResponse(
        UUID publicId,
        String userName,
        String descricao,
        LocalDate dataRegisto,
        LocalTime horaEntrada,
        LocalTime horaSaida,
        double horasTrabalhadas

) {
}
