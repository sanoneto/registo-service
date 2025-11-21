package com.aneto.registo_horas_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Request para registrar horas")
public record RegisterRequest(


        @NotBlank(message = "O  nome estagiario é obrigatório")
        @Size(min = 2, max = 50, message = "Nome deve ter entre 2 e 50 caracteres")
        @Pattern(regexp = "^[A-Za-zÀ-ÿ\\s]+$", message = "Nome deve conter apenas letras")
        String userName,

        @Schema(
                description = "Descrição da atividade",
                example = "Desenvolvimento de API REST",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O primeiro nome é obrigatório")
        @Size(min = 2, max = 50, message = "Nome deve ter entre 2 e 50 caracteres")
        String descricao,


        @Schema(
                description = "Data e hora de início",
                example = "2024-01-15",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                format = "date do registo"
        )
        @NotNull(message = "Data do registo é obrigatória")
        LocalDate dataRegisto,

        @Schema(
                description = " hora de entrada",
                example = "18:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "time",
                format = "date do registo"
        )
        @NotNull(message = "horas de entrada é obrigatória")
        LocalTime horaEntrada,

        @Schema(
                description = " hora de saida",
                example = "22:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "string",
                format = "time"
        )
        @NotNull(message = "Data saida é obrigatória")
        LocalTime horaSaida,

        @Schema(
                description = "Data e hora trabalhadas",
                example = "2.4",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "double"
        )
        double horasTrabalhadas

) {
}
