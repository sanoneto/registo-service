package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;

import java.util.List;

public interface ClinicalTrainingService {
    TrainingPlanResponse generateClinicalPlan(UserProfileRequest userRequest, List<String> exerciciosAnteriores);
}