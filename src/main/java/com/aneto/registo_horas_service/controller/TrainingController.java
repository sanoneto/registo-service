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
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<TrainingPlanResponse> generatePlan(
            @RequestBody(required = false) UserProfileRequest request,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) { // Adicionado o parâmetro id

        // Passamos o request E o planId para o serviço decidir o que fazer
        TrainingPlanResponse response = trainingPlanService.getOrGeneratePlan(request, username, planId);

        return ResponseEntity.ok(response);
    }

}