package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.response.ClinicalBlock;
import com.aneto.registo_horas_service.dto.response.ClinicalDay;
import com.aneto.registo_horas_service.dto.response.ClinicalExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClinicalTrainingValidator {

    private final ExerciseVideoService exerciseVideoService;

    private static final List<String> EXERCICIOS_PROIBIDOS_LOMBAR = List.of(
            "Agachamento Livre", "Levantamento Terra", "Deadlift", "Good Morning",
            "Agachamento Zercher", "Jefferson Curl", "Thruster", "Agachamento com Barra"
    );

    public void validarPlanoClinico(TrainingPlanResponse response, String medicalReportText, String reportVisbodyText) {
        if (response == null) {
            throw new IllegalArgumentException("O plano clínico gerado está nulo.");
        }

        List<ClinicalDay> clinicalDays = response.getClinicalPlan();

        boolean temDiasValidos = clinicalDays != null && !clinicalDays.isEmpty();
        boolean temBlocosLegados = response.getBlocks() != null && !response.getBlocks().isEmpty();

        if (!temDiasValidos && !temBlocosLegados) {
            throw new IllegalArgumentException("O plano clínico gerado está vazio ou não contém dias/blocos válidos.");
        }

        // 1. Validação de Bloco Obrigatório de Descompressão Discal / Libertação
        boolean temDescompressao;
        if (temDiasValidos) {
            temDescompressao = clinicalDays.stream()
                    .filter(day -> day.getBlocks() != null)
                    .flatMap(day -> day.getBlocks().stream())
                    .anyMatch(b -> b.getBlockName() != null &&
                            (b.getBlockName().toUpperCase().contains("LIBERTAÇÃO") ||
                                    b.getBlockName().toUpperCase().contains("DESCOMPRESSÃO") ||
                                    b.getBlockName().toUpperCase().contains("MOBILIDADE")));
        } else {
            temDescompressao = response.getBlocks().stream()
                    .anyMatch(b -> b.getBlockName() != null &&
                            (b.getBlockName().toUpperCase().contains("LIBERTAÇÃO") ||
                                    b.getBlockName().toUpperCase().contains("DESCOMPRESSÃO") ||
                                    b.getBlockName().toUpperCase().contains("MOBILIDADE")));
        }

        if (!temDescompressao) {
            log.error("[VALIDAÇÃO CLÍNICA] Plano rejeitado: Falta do Bloco de Descompressão/Libertação.");
            throw new IllegalArgumentException("VIOLAÇÃO CLÍNICA: O plano de treino DEVE conter obrigatoriamente um Bloco de Descompressão Discal e Libertação.");
        }

        // 2. Validação do Foco no Lado Afetado (Quadríceps / Perna Direita)
        boolean temAssimetriaDireita;
        if (temDiasValidos) {
            temAssimetriaDireita = clinicalDays.stream()
                    .filter(day -> day.getBlocks() != null)
                    .flatMap(day -> day.getBlocks().stream())
                    .filter(b -> b.getExercises() != null)
                    .flatMap(b -> b.getExercises().stream())
                    .anyMatch(ex -> ex.getDosageRight() != null &&
                            (ex.getDosageRight().toLowerCase().contains("dir:") || ex.getDosageRight().toLowerCase().contains("direita")));
        } else {
            temAssimetriaDireita = response.getBlocks().stream()
                    .filter(b -> b.getExercises() != null)
                    .flatMap(b -> b.getExercises().stream())
                    .anyMatch(ex -> ex.getDosageRight() != null &&
                            (ex.getDosageRight().toLowerCase().contains("dir:") || ex.getDosageRight().toLowerCase().contains("direita")));
        }

        if (!temAssimetriaDireita) {
            log.warn("[VALIDAÇÃO CLÍNICA] Alerta: Não foi detetada dosagem específica para a perna direita afetada.");
            throw new IllegalArgumentException("VIOLAÇÃO CLÍNICA: O plano deve indicar dosagem e volume superior para o membro afetado (Perna Direita).");
        }

        // 3. Validação Rígida contra Exercícios de Carga Axial Proibida
        if (temDiasValidos) {
            for (ClinicalDay day : clinicalDays) {
                if (day.getBlocks() == null) continue;
                for (ClinicalBlock block : day.getBlocks()) {
                    validarExerciciosProibidos(block.getExercises());
                }
            }
        } else {
            for (ClinicalBlock block : response.getBlocks()) {
                validarExerciciosProibidos(block.getExercises());
            }
        }
    }

    private void validarExerciciosProibidos(List<ClinicalExercise> exercises) {
        if (exercises == null) return;
        for (ClinicalExercise ex : exercises) {
            if (ex.getName() == null) continue;
            for (String proibido : EXERCICIOS_PROIBIDOS_LOMBAR) {
                if (ex.getName().equalsIgnoreCase(proibido)) {
                    log.error("[BLOQUEIO DE SEGURANÇA] Exercício proibido detetado no plano clínico: '{}'", ex.getName());
                    throw new IllegalArgumentException("VIOLAÇÃO DE SEGURANÇA MÉDICA: O exercício '" + ex.getName() + "' é estritamente PROIBIDO para a patologia do aluno.");
                }
            }
        }
    }

    public void enriquecerExercicoComVideo(ClinicalExercise exercise) {
        if (exercise == null || exercise.getName() == null) return;
        String videoUrl = exerciseVideoService.getVideoUrl(exercise.getName());
        exercise.setVideoUrl(videoUrl != null ? videoUrl : "");
    }
}