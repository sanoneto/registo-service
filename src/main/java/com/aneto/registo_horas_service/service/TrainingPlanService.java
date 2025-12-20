package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;

import java.util.Optional;

public interface TrainingPlanService {
    TrainingPlanResponse generateTrainingPlan(UserProfileRequest request);

    Optional<TrainingPlanResponse> loadFromS3(String key);

    void saveToS3(String key, TrainingPlanResponse plan);

    TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username);


}
