package com.aneto.registo_horas_service.dto.request;

import com.aneto.registo_horas_service.models.Training.BodyType;

import com.aneto.registo_horas_service.models.Training.Gender;
import jakarta.validation.constraints.*;

public record UserProfileRequest(
        @NotNull(message = "Tipo de corpo é obrigatório")
        BodyType bodyType,

        @NotNull(message = "Género é obrigatório")
        Gender gender,

        @Min(0) @Max(120)
        Integer age,

        Double heightCm,
        Double weightKg,
        String exerciseHistory,
        String pathology,

        @NotNull(message = "A frequência deve ser informada")
        @Min(1) @Max(7)
        Integer frequencyPerWeek,

        String objective,
        String trainingLocation,
        String equipmentAvailable,
        String durationPerSession,

        String country,
        String city,
        String location,
        String recommended,

        String studentName,
        String protocol,
        Double bodyFat,
        Integer mealsPerDay
) {
}