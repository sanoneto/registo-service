package com.aneto.registo_horas_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserProfileRequest(String bodyType,
                                 String gender,
                                 Integer age,
                                 Double heightCm, // Usando Double para números decimais
                                 Double weightKg,
                                 String exerciseHistory,
                                 String pathology,        // Inicializado vazio
                                 @NotNull(message = "A frequência deve ser informada")
                                 @Min(1) @Max(7)
                                 Integer frequencyPerWeek,
                                 String objective,
                                 String country,
                                 String location,
                                 String recommended,
                                 String studentName,
                                 String duration,
                                 String protocol,
                                 Double bodyFat
                                 ) {

}
