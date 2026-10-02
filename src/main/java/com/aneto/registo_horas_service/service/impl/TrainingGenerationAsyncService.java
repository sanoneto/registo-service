package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.models.Training.Training;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NOVO: executa a geração pesada do plano (chamada à IA + até 8 retries internos +
 * gravação em S3/BD) FORA da thread do pedido HTTP, para resolver o problema visto em
 * produção (AsyncRequestNotUsableException / Broken pipe ao fim de 119s — o cliente
 * desiste antes do servidor terminar).
 *
 * IMPORTANTE: tem de estar num @Service DIFERENTE de TrainingPlanServiceImpl para o
 * @Async funcionar. Spring implementa @Async através de um proxy; uma chamada interna
 * (this.metodo()) dentro da própria classe não passa pelo proxy e corre de forma síncrona,
 * silenciosamente — é um erro comum e fácil de não notar em testes manuais.
 *
 * Depende de PlanoS3Storage e PlanoRegistoHelper (não de TrainingPlanService) para evitar
 * uma dependência circular com TrainingPlanServiceImpl — ver comentário em PlanoS3Storage.
 *
 * Requer @EnableAsync com um TaskExecutor chamado "trainingTaskExecutor" — ver AsyncConfig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingGenerationAsyncService {

    private final Training training;
    private final PlanoRegistoHelper planoRegistoHelper;
    private final PlanoS3Storage planoS3Storage;

    @Async("trainingTaskExecutor")
    public void gerarEGuardarPlano(UserProfileRequest request, String username, String planId, String key,
                                   int weekNumber, boolean isDeloadWeek, List<String> exerciciosParaEvitar) {
        try {
            log.info("[gerarEGuardarPlano] INÍCIO (background) | username='{}' | planId='{}' | semana={}",
                    username, planId, weekNumber);

            TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);
            newPlan.setIsExistingPlan(true);
            newPlan.setUserProfile(request);

            planoS3Storage.saveToS3(key, newPlan);

            // planId aqui já é o id DEFINITIVO devolvido pelo registo A_PROCESSAR anterior
            // (nunca null) — por isso esta chamada atualiza o mesmo registo, não cria outro.
            planoRegistoHelper.upsert(username, request, key, planId, weekNumber, isDeloadWeek,
                    Enum.EstadoPedido.PENDENTE, null);

            log.info("[gerarEGuardarPlano] CONCLUÍDO com sucesso | username='{}' | planId='{}'", username, planId);

        } catch (Exception e) {
            log.error("[gerarEGuardarPlano] FALHA na geração em background | username='{}' | planId='{}'",
                    username, planId, e);
            try {
                planoRegistoHelper.upsert(username, request, key, planId, weekNumber, isDeloadWeek,
                        Enum.EstadoPedido.ERRO, resumirErro(e));
            } catch (Exception erroAoRegistar) {
                // Pior cenário: nem sequer conseguimos marcar o estado como ERRO. Fica em
                // A_PROCESSAR indefinidamente — fica registado em log a ERROR para deteção manual.
                log.error("[gerarEGuardarPlano] Falha ADICIONAL ao tentar registar o estado de erro | username='{}' | planId='{}'",
                        username, planId, erroAoRegistar);
            }
        }
    }

    private String resumirErro(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Falha desconhecida ao gerar o plano.";
        return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
    }
}