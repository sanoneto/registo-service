package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {
    private final GeminiClient aiClient;

    @Override
    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest request) {
        // A lógica de chamada, retry e desserialização está no cliente.
        return aiClient.generateTrainingPlan(request);
    }
}