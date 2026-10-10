package com.aneto.registo_horas_service.dto.response;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainingPlanResponse implements Serializable {

    private Boolean isExistingPlan;

    @JsonAlias("clinicalSummary")
    private String summary;

    @Builder.Default
    @JsonProperty("plan")
    private List<TrainingDay> plan = new ArrayList<>();

    @JsonProperty("dietPlan")
    private DietPlan dietPlan;

    @JsonProperty("userProfile")
    private UserProfileRequest userProfile;

    @JsonProperty("primaryDiagnosis")
    private String primaryDiagnosis;

    @JsonProperty("primaryObjective")
    private String primaryObjective;

    @JsonProperty("validationStatus")
    private String validationStatus;

    @Builder.Default
    @JsonProperty("safetyDirectives")
    private List<String> safetyDirectives = new ArrayList<>();

    @Builder.Default
    @JsonProperty("blocks")
    private List<ClinicalBlock> blocks = new ArrayList<>();

    @Builder.Default
    @JsonProperty("clinicalPlan")
    private List<ClinicalDay> clinicalPlan = new ArrayList<>();
}