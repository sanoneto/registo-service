package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ExerciseHistoryResponse;
import com.aneto.registo_horas_service.dto.response.ExerciseProgressLog;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import com.aneto.registo_horas_service.service.PlanoService;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import com.aneto.registo_horas_service.service.impl.MedicalReportExtractionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingPlanService trainingPlanService;
    private final PlanoService planoService;
    private static final String X_USER_ID = "X-User-Id";
    private final MedicalReportExtractionServiceImpl medicalReportExtractionService;

    @PostMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') or hasRole('USER') and #username == authentication.name)")
    public ResponseEntity<TrainingPlanResponse> generatePlan(
            @RequestBody(required = false) UserProfileRequest request,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) {

        boolean temAlunoComConta = request != null
                && request.getStudentUsername() != null
                && !request.getStudentUsername().isBlank();

        if (temAlunoComConta) {
            // Plano para aluno com conta: comportamento normal, dono = o aluno.
            username = request.getStudentUsername();
        } else if (request != null) {
            // Aluno "novo"/sem conta.
            request.setStudentUsername(null);

            if (planId != null && !planId.isBlank()) {
                // A ATUALIZAR um plano existente: reaproveita o alunoTempId já gravado,
                // para não perder a periodização/histórico deste aluno fictício.
                try {
                    PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));
                    request.setAlunoTempId(
                            planoExistente != null && planoExistente.getAlunoTempId() != null
                                    ? planoExistente.getAlunoTempId()
                                    : UUID.randomUUID().toString()
                    );
                } catch (Exception e) {
                    log.warn("Não foi possível recuperar alunoTempId do plano {} — a gerar um novo.", planId);
                    request.setAlunoTempId(UUID.randomUUID().toString());
                }
            } else {
                // Plano novo: gera um identificador novo para este aluno fictício.
                request.setAlunoTempId(UUID.randomUUID().toString());
            }
        }

        if (request != null && (request.getStudentName() == null || request.getStudentName().isBlank())) {
            request.setStudentName(username);
        }

        log.info("A gerar plano. Aluno com conta? {} | username usado: '{}' | studentName: '{}' | alunoTempId: '{}'",
                temAlunoComConta, username,
                request != null ? request.getStudentName() : null,
                request != null ? request.getAlunoTempId() : null);

        TrainingPlanResponse response = trainingPlanService.getOrGeneratePlan(request, username, planId);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/medical-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> extractMedicalReport(
            @RequestPart("file") MultipartFile file) {

        try {
            String text = medicalReportExtractionService.extractText(file);
            return ResponseEntity.ok(Map.of("medicalReportText", text));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao extrair relatório médico: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Falha ao processar o ficheiro."));
        }
    }

    @PutMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') or hasRole('USER') and #username == authentication.name)")
    public ResponseEntity<?> updatePlan(
            @RequestBody TrainingPlanResponse plan,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) {

        trainingPlanService.updatePlan(plan, username, planId);

        return ResponseEntity.ok(Map.of("message", "Plano atualizado com sucesso"));
    }

    @PostMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<?> saveProgress(
            @RequestBody List<TrainingExercise> logs,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "planId", required = false) String planId) {

        trainingPlanService.saveProgressLogs(logs, username, planId);
        return ResponseEntity.ok(Map.of("message", "Progresso guardado com sucesso"));
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<List<ExerciseHistoryResponse>> getProgress(
            @RequestParam String username,
            @RequestParam String exerciseName) {

        List<ExerciseHistoryResponse> history = trainingPlanService.getProgressLogs(exerciseName, username);

        return ResponseEntity.ok(history);
    }
    //14-04-2024
    // >>> NOVO: associar um plano de "aluno sem conta" a uma conta real, quando ela existir
    @PutMapping("/plan/{planId}/associar-conta")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA')")
    public ResponseEntity<?> associarConta(
            @PathVariable String planId,
            @RequestParam String novoUsername) {

        trainingPlanService.associarPlanoAConta(planId, novoUsername);
        return ResponseEntity.ok(Map.of("message", "Plano associado ao aluno com sucesso"));
    }
}