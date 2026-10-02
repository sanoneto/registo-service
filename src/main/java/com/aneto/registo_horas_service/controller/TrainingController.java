package com.aneto.registo_horas_service.controller;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ExerciseHistoryResponse;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.dto.response.PlanoStatusResponse;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.MedicalReportExtractionService;
import com.aneto.registo_horas_service.service.PlanoService;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final MedicalReportExtractionService medicalReportExtractionServiceImpl;
    private final MedicalReportExtractionService reportReportVisbodyServiceImpl;


    // CORRIGIDO: tipo de retorno passou de ResponseEntity<TrainingPlanResponse> para
    // não pelo formato do corpo.
    @PostMapping("/plan")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ESPECIALISTA') or (hasRole('ESTAGIARIO') or hasRole('USER') and #username == authentication.name)")
    public ResponseEntity<?> generatePlan(
            @RequestBody(required = false) UserProfileRequest request,
            @RequestHeader(X_USER_ID) String username,
            @RequestParam(value = "id", required = false) String planId) {

        long inicio = System.currentTimeMillis();

        // 1) O QUE CHEGA (inalterado)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("[generatePlan] ENTRADA | header {}='{}' | auth.name='{}' | roles={} | planId='{}' | body presente? {}",
                X_USER_ID, username,
                auth != null ? auth.getName() : null,
                auth != null ? auth.getAuthorities() : null,
                planId,
                request != null);

        if (request != null) {
            log.info("[generatePlan] BODY | studentUsername='{}' | studentName='{}' | alunoTempId='{}'",
                    request.getStudentUsername(), request.getStudentName(), request.getAlunoTempId());
        }

        boolean temAlunoComConta = request != null
                && request.getStudentUsername() != null
                && !request.getStudentUsername().isBlank();

        if (temAlunoComConta) {
            username = request.getStudentUsername();
            log.info("[generatePlan] RAMO 1: aluno com conta -> username='{}'", username);
        } else if (request != null) {
            request.setStudentUsername(null);

            if (planId != null && !planId.isBlank()) {
                try {
                    PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));
                    log.info("[generatePlan] RAMO 2: plano {} encontrado na BD? {} | alunoTempId existente='{}'",
                            planId, planoExistente != null,
                            planoExistente != null ? planoExistente.getAlunoTempId() : null);
                    request.setAlunoTempId(
                            planoExistente != null && planoExistente.getAlunoTempId() != null
                                    ? planoExistente.getAlunoTempId()
                                    : UUID.randomUUID().toString()
                    );
                } catch (Exception e) {
                    log.warn("[generatePlan] RAMO 2: erro a recuperar alunoTempId do plano {} — a gerar um novo.", planId, e);
                    request.setAlunoTempId(UUID.randomUUID().toString());
                }
            } else {
                request.setAlunoTempId(UUID.randomUUID().toString());
                log.info("[generatePlan] RAMO 3: plano novo -> alunoTempId gerado='{}'", request.getAlunoTempId());
            }
        } else {
            // request == null: é o caso do botão "ver"
            log.info("[generatePlan] RAMO 4: SEM BODY (modo leitura) | username='{}' | planId='{}'", username, planId);
        }

        log.info("A gerar plano. Aluno com conta? {} | username usado: '{}' | studentName: '{}' | alunoTempId: '{}'",
                temAlunoComConta, username,
                request != null ? request.getStudentName() : null,
                request != null ? request.getAlunoTempId() : null);

        // NOVO: decide entre caminho de leitura (síncrono, inalterado) e caminho de
        // geração (agora assíncrono). isPedidoDeGeracaoCompleto reutiliza exatamente a
        // mesma condição que já decidia isto dentro do serviço — sem duplicar lógica.
        boolean pedidoDeGeracaoCompleto = request != null && trainingPlanService.isPedidoDeGeracaoCompleto(request);

        if (!pedidoDeGeracaoCompleto) {
            // 2) CAMINHO DE LEITURA — exatamente como antes, síncrono (já é rápido, só lê do S3).
            TrainingPlanResponse response;
            try {
                response = trainingPlanService.getOrGeneratePlan(request, username, planId);
            } catch (Exception e) {
                log.error("[generatePlan] ERRO no serviço | username='{}' | planId='{}'", username, planId, e);
                throw e;
            }

            log.info("[generatePlan] RESULTADO | response {} | {} ms",
                    response == null ? "É NULL" : "OK", System.currentTimeMillis() - inicio);

            if (response == null) {
                log.warn("[generatePlan] Serviço devolveu NULL (username='{}', planId='{}') -> 404", username, planId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(response);
        }

        // NOVO: CAMINHO DE GERAÇÃO — assíncrono. Regista o plano em A_PROCESSAR, dispara a
        // geração em background e devolve de imediato (HTTP 202), em vez de bloquear o
        // pedido HTTP pelos ~1-2 minutos que a geração + retries podem levar (ver
        // AsyncRequestNotUsableException / Broken pipe observado em produção).
        PlanoResponseDTO planoIniciado;
        try {
            planoIniciado = trainingPlanService.iniciarGeracaoAssincrona(request, username, planId);
        } catch (Exception e) {
            log.error("[generatePlan] ERRO ao iniciar geração assíncrona | username='{}' | planId='{}'", username, planId, e);
            throw e;
        }

        log.info("[generatePlan] Geração assíncrona iniciada | planId='{}' | {} ms",
                planoIniciado.getId(), System.currentTimeMillis() - inicio);

        return ResponseEntity.accepted().body(planoIniciado);
    }

    // NOVO: endpoint de polling. O frontend chama isto de poucos em poucos segundos
    // depois de receber o 202 de /plan, até ver estadoPedido = "FINALIZADO" (ou "ERRO").
    @GetMapping("/plan/{planId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanoStatusResponse> getPlanStatus(@PathVariable String planId) {
        PlanoStatusResponse status = trainingPlanService.getStatusDoPlano(planId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping(value = "/visualbody-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> extractVisualBOdyReport(
            @RequestPart("file") MultipartFile file) {
        try {
            String text = reportReportVisbodyServiceImpl.extractText(file);
            return ResponseEntity.ok(Map.of("visualBodyReportText", text));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao extrair visual body : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Falha ao processar o ficheiro."));
        }
    }

    @PostMapping(value = "/medical-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> extractMedicalReport(
            @RequestPart("file") MultipartFile file) {

        try {
            String text = medicalReportExtractionServiceImpl.extractText(file);
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

    // 14-04-2024
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