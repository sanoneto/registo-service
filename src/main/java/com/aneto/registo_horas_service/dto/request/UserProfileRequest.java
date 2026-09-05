package com.aneto.registo_horas_service.dto.request;

import com.aneto.registo_horas_service.models.Training.BodyType;
import com.aneto.registo_horas_service.models.Training.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transformado de Record para Classe DTO para garantir compatibilidade
 * total com a serialização do Jackson e evitar UnsupportedOperationException.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileRequest {

    private String studentUsername;

    @NotNull(message = "Tipo de corpo é obrigatório")
    private BodyType bodyType;

    @NotNull(message = "Género é obrigatório")
    private Gender gender;

    @Min(0) @Max(120)
    private Integer age;

    private Double heightCm;
    private Double weightKg;
    private String exerciseHistory;
    private String pathology;

    @NotNull(message = "A frequência deve ser informada")
    @Min(1) @Max(7)
    private Integer frequencyPerWeek;

    private String objective;
    private String trainingLocation;
    private String equipmentAvailable;
    private String durationPerSession;

    private String country;
    private String city;
    private String location;
    private String recommended;

    private String studentName;
    private String protocol;
    private Double bodyFat;
    private Integer mealsPerDay;
}