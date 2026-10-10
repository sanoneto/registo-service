package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.response.PatologiaInferida;
import com.aneto.registo_horas_service.dto.response.TrainingDay;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.aneto.registo_horas_service.models.Training.TrainingRuleConstants.*;
import static com.aneto.registo_horas_service.models.Training.TrainingUtils.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrainingValidator {

    private final ExerciseVideoService exerciseVideoService;

    // Exercícios proibidos para patologias lombares e anteversão pélvica severa
    private static final List<String> EXERCICIOS_PROIBIDOS_LOMBAR = List.of(
            "Agachamento Livre", "Levantamento Terra", "Good Morning",
            "Agachamento Zercher", "Jefferson Curl", "Thruster", "Agachamento com Barra"
    );

    public void validarSegurancaLombarEVisbody(TrainingPlanResponse response, String medicalReportText, String reportVisbodyText) {
        if (response == null || response.getPlan() == null) return;

        String medicoLower = medicalReportText != null ? medicalReportText.toLowerCase() : "";
        String visbodyLower = reportVisbodyText != null ? reportVisbodyText.toLowerCase() : "";

        boolean temLesaoLombar = medicoLower.contains("lombar")
                || medicoLower.contains("anterolistese")
                || medicoLower.contains("discal")
                || visbodyLower.contains("anteversão")
                || visbodyLower.contains("pélvica");

        if (!temLesaoLombar) return;

        for (TrainingDay day : response.getPlan()) {
            if (day.getExercises() == null) continue;
            for (TrainingExercise ex : day.getExercises()) {
                if (ex.getName() == null) continue;
                for (String proibido : EXERCICIOS_PROIBIDOS_LOMBAR) {
                    if (ex.getName().equalsIgnoreCase(proibido)) {
                        log.error("[BLOQUEIO DE SEGURANÇA] Exercício proibido detetado: '{}' no dia '{}'", ex.getName(), day.getDay());
                        throw new IllegalArgumentException(
                                "VIOLAÇÃO CRÍTICA DE SEGURANÇA MÉDICA: O exercício '" + ex.getName() +
                                        "' é estritamente PROIBIDO para utentes com diagnóstico de lesão lombar/anteversão."
                        );
                    }
                }
            }
        }
    }

    public TrainingExercise enrichExercise(TrainingExercise ex, Set<String> validNames, String pathologyText) {
        if (ex == null || ex.getName() == null) {
            throw new IllegalArgumentException("Exercício recebido do modelo de IA não contém nome válido.");
        }

        String correctedName = normalizeExerciseName(ex.getName(), validNames);

        if (!validNames.contains(correctedName)) {
            log.error("[ALERTA DE INVENTÁRIO] Nome fora do dicionário oficial: '{}' (original: '{}')", correctedName, ex.getName());
            throw new RuntimeException("Exercício fora do inventário: \"" + ex.getName() + "\". Substitui por um nome do dicionário.");
        }
        validarMobilidadeCondicional(correctedName, pathologyText);

        String finalUrl = exerciseVideoService.getVideoUrl(correctedName);

        return TrainingExercise.builder()
                .order(ex.getOrder())
                .name(correctedName)
                .muscleGroup(ex.getMuscleGroup())
                .equipment(ex.getEquipment())
                .intensity(ex.getIntensity())
                .sets(ex.getSets())
                .reps(ex.getReps())
                .rest(ex.getRest())
                .tempo(ex.getTempo())
                .details(ex.getDetails())
                .notas(ex.getNotas())
                .weight(ex.getWeight())
                .cargaAtual(ex.getCargaAtual())
                .videoUrl(finalUrl)
                .date(java.time.LocalDate.now().toString())
                .movementPlane(ex.getMovementPlane())
                .build();
    }

    public void validarSequenciaDias(List<TrainingDay> plan, boolean aplicaFocoGluteoExtremo) {
        if (plan == null) return;
        String categoriaAnterior = null;
        for (TrainingDay day : plan) {
            String categoriaAtual = extrairCategoriaDia(day.getDay());
            boolean repeticaoPermitida = aplicaFocoGluteoExtremo && "LEGS".equalsIgnoreCase(categoriaAtual);
            if (categoriaAtual != null && categoriaAtual.equalsIgnoreCase(categoriaAnterior) && !repeticaoPermitida) {
                throw new RuntimeException("Repetição de categoria em dias consecutivos: \"" + categoriaAtual + "\" (dia \"" + day.getDay() + "\").");
            }
            categoriaAnterior = categoriaAtual;
        }
    }

    public String extrairCategoriaDia(String dayLabel) {
        if (dayLabel == null) return null;
        int idxTraco = dayLabel.indexOf('-');
        int idxDoisPontos = dayLabel.indexOf(':');
        if (idxTraco == -1 || idxDoisPontos == -1 || idxDoisPontos <= idxTraco) return null;
        return dayLabel.substring(idxTraco + 1, idxDoisPontos).trim();
    }

    public void validarProporcaoUpperVisbody(TrainingPlanResponse response, String reportVisbodyText) {
        if (response == null || response.getPlan() == null || reportVisbodyText == null || reportVisbodyText.isBlank()) return;

        boolean temProtracaoOmbros = reportVisbodyText.toLowerCase().contains("protração")
                || reportVisbodyText.toLowerCase().contains("enrolados")
                || reportVisbodyText.toLowerCase().contains("cabeça projetada");

        if (!temProtracaoOmbros) return;

        for (TrainingDay day : response.getPlan()) {
            if (day.getDay() != null && day.getDay().toUpperCase().contains("UPPER")) {
                long exPeito = day.getExercises().stream()
                        .filter(e -> e.getMuscleGroup() != null && e.getMuscleGroup().equalsIgnoreCase("Peito"))
                        .count();

                long exCostasEOmbros = day.getExercises().stream()
                        .filter(e -> e.getMuscleGroup() != null &&
                                (e.getMuscleGroup().equalsIgnoreCase("Costas")
                                        || e.getMuscleGroup().equalsIgnoreCase("Costas / Cintura Escapular")
                                        || e.getMuscleGroup().equalsIgnoreCase("Ombros")))
                        .count();

                if (exPeito >= exCostasEOmbros) {
                    log.warn("[BLOQUEIO POSTURAL] Dia UPPER com {} ex. de Peito e {} de Costas/Ombros.", exPeito, exCostasEOmbros);
                    throw new IllegalArgumentException(
                            "VIOLAÇÃO DE CORREÇÃO POSTURAL: Utente apresenta ombros enrolados. " +
                                    "O número de exercícios de Costas/Cadeia Posterior (" + exCostasEOmbros + ") deve ser o dobro em relação ao Peito (" + exPeito + ")."
                    );
                }
            }
        }
    }

    private static final Map<String, String> ACHADOS_MEDICOS_PARA_PATOLOGIA = Map.ofEntries(
            Map.entry("retrolistese", "Lesão Lombar"),
            Map.entry("discopatia", "Lesão Lombar"),
            Map.entry("protrusão discal", "Hérnia de Disco"),
            Map.entry("protrusao discal", "Hérnia de Disco"),
            Map.entry("hérnia discal", "Hérnia de Disco"),
            Map.entry("hernia discal", "Hérnia de Disco"),
            Map.entry("radicular", "Lesão Lombar"),
            Map.entry("lombar", "Lesão Lombar"),
            Map.entry("espondilose", "Lesão Lombar"),
            Map.entry("anterolistese", "Lesão Lombar"),
            Map.entry("escoliose", "Lesão Lombar"),
            Map.entry("manguito rotador", "Lesão Ombro"),
            Map.entry("bursite subacromial", "Lesão Ombro"),
            Map.entry("menisco", "Lesão Joelho"),
            Map.entry("ligamento cruzado", "Lesão Joelho"),
            Map.entry("condromalácia", "Lesão Joelho")
    );

    public PatologiaInferida inferirPatologiaDoRelatorio(String medicalReportText) {
        if (medicalReportText == null || medicalReportText.isBlank()) return null;
        String lower = medicalReportText.toLowerCase();

        LinkedHashMap<String, List<String>> categoriaParaTermos = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ACHADOS_MEDICOS_PARA_PATOLOGIA.entrySet()) {
            if (lower.contains(entry.getKey())) {
                categoriaParaTermos
                        .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }

        if (categoriaParaTermos.isEmpty()) return null;

        String categoriasJuntas = String.join(", ", categoriaParaTermos.keySet());
        Set<String> todosTermos = categoriaParaTermos.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new PatologiaInferida(categoriasJuntas, todosTermos);
    }
}