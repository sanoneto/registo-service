package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingPlanService trainingPlanService;
    private static final String X_USER_ID = "X-User-Id";


    @PostMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<TrainingPlanResponse> generatePlan(@RequestBody UserProfileRequest request, @RequestHeader(X_USER_ID) String username) {
        // Verifica se a requisição base é válida
        if (request == null || request.gender() == null || request.weightKg() == null) {
            return ResponseEntity.badRequest().build();
        }

        TrainingPlanResponse response = trainingPlanService.generateTrainingPlan(request);

        return ResponseEntity.ok(response);
    }

}