package com.aneto.registo_horas_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalTrainingPlanResponse {
    private boolean isExistingPlan;
    private String clientName;
    private Integer age;
    private Double weightKg;
    private String primaryDiagnosis;
    private String primaryObjective;
    private String clinicalSummary;
    private String validationStatus;

    @Builder.Default
    private List<String> safetyDirectives = new ArrayList<>();

    @Builder.Default
    private List<ClinicalDay> clinicalPlan = new ArrayList<>();

    @Builder.Default
    private List<ClinicalBlock> blocks = new ArrayList<>();

    private DietPlan dietPlan;
}