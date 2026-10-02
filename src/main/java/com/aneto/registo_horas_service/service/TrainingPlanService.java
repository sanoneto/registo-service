package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;

import java.util.List;
import java.util.Optional;

public interface TrainingPlanService {

    Optional<TrainingPlanResponse> loadFromS3(String key);

    void saveToS3(String key, TrainingPlanResponse plan);

    TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username, String planId);

    void updatePlan(TrainingPlanResponse plan, String username, String planId);


    void saveProgressLogs(List<TrainingExercise> logs, String username, String planId);

    List<ExerciseHistoryResponse> getProgressLogs(String exerciseName, String username);

    void associarPlanoAConta(String planId, String novoUsername);

    boolean isPedidoDeGeracaoCompleto(UserProfileRequest request);

    PlanoResponseDTO iniciarGeracaoAssincrona(UserProfileRequest request, String username, String planId);

    PlanoStatusResponse getStatusDoPlano(String planId);
}
