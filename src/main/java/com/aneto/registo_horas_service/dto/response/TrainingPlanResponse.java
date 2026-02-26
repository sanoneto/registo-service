package com.aneto.registo_horas_service.dto.response;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Ignora propriedades chatas do Hibernate que podem estar no UserProfileRequest
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TrainingPlanResponse implements Serializable {

    private Boolean isExistingPlan;
    private String summary;

    // Inicializar com ArrayList evita que o Jackson pegue uma lista imutável vazia
    @Builder.Default
    private List<TrainingDay> plan = new ArrayList<>();

    private DietPlan dietPlan;

    // Usamos o seu novo DTO ou o Request transformado em classe
    private UserProfileRequest userProfile;
}