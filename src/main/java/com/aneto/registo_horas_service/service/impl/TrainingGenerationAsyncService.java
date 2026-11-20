package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ClinicalDay;
import com.aneto.registo_horas_service.dto.response.TrainingDay;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.Enum.EstadoPedido;
import com.aneto.registo_horas_service.models.Training.Training;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingGenerationAsyncService {

    private final Training training;
    private final PlanoRegistoHelper planoRegistoHelper;
    // Leitura: S3 primeiro, BD como fallback. Escrita: BD + S3 (ver PlanoStorage)
    private final PlanoStorage planoStorage;

    @Async("trainingTaskExecutor")
    public void gerarEGuardarPlano(UserProfileRequest request, String username, String planId, String key,
                                   int weekNumber, boolean isDeloadWeek, List<String> exerciciosParaEvitar) {
        try {
            log.info("[gerarEGuardarPlano] INÍCIO (background) | username='{}' | planId='{}' | semana={}",
                    username, planId, weekNumber);

            TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);

            // 1. CRÍTICO: Mapear clinicalPlan/blocks para a lista 'plan', preservando explicitamente o videoUrl
            sincronizarBlocosComPlanoClassico(newPlan);

            newPlan.setIsExistingPlan(true);
            newPlan.setUserProfile(request);

            // 2. Grava (BD + S3) com o array 'plan' devidamente preenchido com as URLs dos vídeos
            planoStorage.save(key, newPlan);

            // 3. Atualiza estado na BD (só depois de o JSON estar gravado)
            planoRegistoHelper.upsert(username, request, key, planId, weekNumber, isDeloadWeek,
                    EstadoPedido.PENDENTE, null);

            log.info("[gerarEGuardarPlano] CONCLUÍDO com sucesso | username='{}' | planId='{}'", username, planId);

        } catch (Exception e) {
            log.error("[gerarEGuardarPlano] FALHA na geração em background | username='{}' | planId='{}'",
                    username, planId, e);
            try {
                planoRegistoHelper.upsert(username, request, key, planId, weekNumber, isDeloadWeek,
                        EstadoPedido.ERRO, resumirErro(e));
            } catch (Exception erroAoRegistar) {
                log.error("[gerarEGuardarPlano] Falha ADICIONAL ao tentar registar o estado de erro | username='{}' | planId='{}'",
                        username, planId, erroAoRegistar);
            }
        }
    }

    /**
     * Converte a estrutura de 'clinicalPlan' (multi-dias) ou 'blocks' (dia único)
     * para a lista de treino legada 'plan', garantindo que o 'videoUrl' não se perde.
     */
    public void sincronizarBlocosComPlanoClassico(TrainingPlanResponse newPlan) {
        if (newPlan == null) return;

        // Se a lista 'plan' já tiver dados populados, não faz sobrescrita
        if (newPlan.getPlan() != null && !newPlan.getPlan().isEmpty()) {
            return;
        }

        // 1. Processamento a partir do 'clinicalPlan' (Plano Clínico Multi-Dias)
        if (newPlan.getClinicalPlan() != null && !newPlan.getClinicalPlan().isEmpty()) {
            List<TrainingDay> days = new ArrayList<>();

            for (ClinicalDay cDay : newPlan.getClinicalPlan()) {
                TrainingDay day = new TrainingDay();
                day.setDay(cDay.getDay());
                day.setExercises(cDay.getExercises());
                days.add(day);
            }
            newPlan.setPlan(days);
        }

    }

    private String resumirErro(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Falha desconhecida ao gerar o plano.";
        return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
    }
}