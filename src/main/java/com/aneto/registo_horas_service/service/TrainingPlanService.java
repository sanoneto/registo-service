package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;

public interface TrainingPlanService {
    TrainingPlanResponse generateTrainingPlan(UserProfileRequest request);
}
