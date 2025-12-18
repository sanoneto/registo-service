package com.aneto.registo_horas_service.dto.response;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanResponse

{
    private  Boolean isExistingPlan;
    private  String summary;
    private List<TrainingDay> plan;
    private DietPlan dietPlan;
    private UserProfileRequest userProfile; // ADICIONE ESTE CAMPO
}