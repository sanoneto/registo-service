package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ExerciseProgressLog;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    @PutMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') and #username == authentication.name)")
    public ResponseEntity<?> updatePlan(
            @RequestBody TrainingPlanResponse plan,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) { // Adicionado o parâmetro id

        // Passamos o request E o planId para o serviço decidir o que fazer
        trainingPlanService.updatePlan(plan, username, planId);

        // Opção 1: Retorna 200 OK com um mapa de mensagem (mais comum para APIs JS/React)
        return ResponseEntity.ok(Map.of("message", "Plano atualizado com sucesso"));
    }

    @PostMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'ALUNO')")
    public ResponseEntity<?> saveProgress(
            @RequestBody List<TrainingExercise> logs, // Agora o símbolo será resolvido
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "planId", required = false) String planId) {

        trainingPlanService.saveProgressLogs(logs, username, planId);
        return ResponseEntity.ok(Map.of("message", "Progresso guardado com sucesso"));
    }
}