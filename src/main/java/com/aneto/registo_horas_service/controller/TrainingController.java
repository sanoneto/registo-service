package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ExerciseHistoryResponse;
import com.aneto.registo_horas_service.dto.response.ExerciseProgressLog;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
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

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingPlanService trainingPlanService;
    private static final String X_USER_ID = "X-User-Id";
    private final MedicalReportExtractionServiceImpl medicalReportExtractionService;

    @PostMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') or hasRole('USER') and #username == authentication.name)")
    public ResponseEntity<TrainingPlanResponse> generatePlan(
            @RequestBody(required = false) UserProfileRequest request,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) {

        if (request != null && request.getStudentUsername() != null) {
            username = request.getStudentUsername();
        }
        if (request != null && request.getStudentName() == null) {
            request.setStudentName(username);
        }

        // request.getMedicalReportText() já vem preenchido (ou null) — nada muda aqui,
        // só segue dentro do request para o service tratar.
        TrainingPlanResponse response = trainingPlanService.getOrGeneratePlan(request, username, planId);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/medical-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()") // qualquer utilizador autenticado pode extrair o seu próprio relatório
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
            @RequestParam(value = "id", required = false) String planId) { // Adicionado o parâmetro id

        // Passamos o request E o planId para o serviço decidir o que fazer
        trainingPlanService.updatePlan(plan, username, planId);

        // Opção 1: Retorna 200 OK com um mapa de mensagem (mais comum para APIs JS/React)
        return ResponseEntity.ok(Map.of("message", "Plano atualizado com sucesso"));
    }

    @PostMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<?> saveProgress(
            @RequestBody List<TrainingExercise> logs, // Agora o símbolo será resolvido
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "planId", required = false) String planId) {

        trainingPlanService.saveProgressLogs(logs, username, planId);
        return ResponseEntity.ok(Map.of("message", "Progresso guardado com sucesso"));
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESPECIALISTA', 'ESTAGIARIO', 'USER')")
    public ResponseEntity<List<ExerciseHistoryResponse>> getProgress(
            @RequestParam  String username,
            @RequestParam String exerciseName) {

        // Chama o serviço para procurar a lista de logs filtrada
        List<ExerciseHistoryResponse> history = trainingPlanService.getProgressLogs(exerciseName, username);

        return ResponseEntity.ok(history);
    }
}