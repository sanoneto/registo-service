package com.aneto.registo_horas_service.dto.request;

import com.aneto.registo_horas_service.models.Enum;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AbsenceRequest(
        @NotNull(message = "O tipo de ausência é obrigatório.")
        String userName,
        Enum.AbsenceType type,
        @NotNull(message = "A data de início é obrigatória.")
        @FutureOrPresent(message = "A data de início deve ser hoje ou futura.")
        LocalDate startDate,
        @NotNull(message = "A data de fim é obrigatória.")
        LocalDate endDate,
        @NotBlank(message = "O motivo é obrigatório.")
        @Size(min = 5, max = 255, message = "O motivo deve ter entre 5 e 255 caracteres.")
        String reason
) {
}
