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

    // =========================================================================
    // 1. VALIDAÇÃO RÍGIDA DE SEGURANÇA (MÉDICA & POSTURAL - VISBODY)
    // =========================================================================
    public void validarSegurancaLombarEVisbody(TrainingPlanResponse response, String medicalReportText, String reportVisbodyText) {
        if (response == null || response.getPlan() == null) return;

        String medicoLower = medicalReportText != null ? medicalReportText.toLowerCase() : "";
        String visbodyLower = reportVisbodyText != null ? reportVisbodyText.toLowerCase() : "";

        boolean temLesaoLombar = medicoLower.contains("lombar") || visbodyLower.contains("anteversão") || visbodyLower.contains("pélvica");

        if (!temLesaoLombar) return;

        // Lista de exercícios com alta compressão ou cisalhamento na coluna lombar
        List<String> exerciciosProibidosLombar = List.of(
                "Agachamento Livre", "Levantamento Terra", "Good Morning",
                "Agachamento Zercher", "Jefferson Curl", "Thruster"
        );

        for (TrainingDay day : response.getPlan()) {
            if (day.getExercises() == null) continue;
            for (TrainingExercise ex : day.getExercises()) {
                if (ex.getName() == null) continue;
                for (String proibido : exerciciosProibidosLombar) {
                    if (ex.getName().equalsIgnoreCase(proibido)) {
                        log.error("[BLOQUEIO DE SEGURANÇA] Exercício proibido detetado: '{}' em '{}", ex.getName(), day.getDay());
                        throw new IllegalArgumentException(
                                "VIOLAÇÃO CRÍTICA DE SEGURANÇA MÉDICA: O exercício '" + ex.getName() +
                                        "' é estritamente PROIBIDO para alunos com lesão lombar e anteversão pélvica."
                        );
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. ENRIQUECIMENTO E VALIDAÇÃO INDIVIDUAL DE EXERCÍCIO
    // =========================================================================
    public TrainingExercise enrichExercise(TrainingExercise ex, Set<String> validNames, String pathologyText) {
        String correctedName = normalizeExerciseName(ex.getName(), validNames);

        if (!validNames.contains(correctedName)) {
            log.error("[ALERTA DE INVENTÁRIO] Nome fora do dicionário: '{}' (original: '{}')", correctedName, ex.getName());
            throw new RuntimeException("Exercício fora do inventário: \"" + ex.getName() + "\". Substitui por um nome válido.");
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

    // =========================================================================
    // 3. VALIDAÇÃO DE SEQUÊNCIA DE DIAS
    // =========================================================================
    public void validarSequenciaDias(List<TrainingDay> plan, boolean aplicaFocoGluteoExtremo) {
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

    public int obterExigenciaCardioDoDia(String dayLabel, String categoriaDoDia) {
        if ("CORE".equalsIgnoreCase(categoriaDoDia)) return CARDIO_OBRIGATORIO_POR_DIA_CORE;
        if ("MANUTENÇÃO".equalsIgnoreCase(categoriaDoDia) && dayLabel != null && dayLabel.toLowerCase().contains("core")) {
            return CARDIO_OBRIGATORIO_POR_DIA_MANUTENCAO;
        }
        return 0;
    }

    // =========================================================================
    // 4. MAPEAMENTO DE TERMOS MÉDICOS PARA PATOLOGIAS
    // =========================================================================
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
            Map.entry("espondilólise", "Lesão Lombar"),
            Map.entry("escoliose", "Lesão Lombar"),
            Map.entry("manguito rotador", "Lesão Ombro"),
            Map.entry("bursite subacromial", "Lesão Ombro"),
            Map.entry("tendinite do supraespinhoso", "Lesão Ombro"),
            Map.entry("capsulite adesiva", "Lesão Ombro"),
            Map.entry("luxação do ombro", "Lesão Ombro"),
            Map.entry("menisco", "Lesão Joelho"),
            Map.entry("ligamento cruzado", "Lesão Joelho"),
            Map.entry("condromalácia", "Lesão Joelho"),
            Map.entry("condromalacia", "Lesão Joelho"),
            Map.entry("tendinite patelar", "Lesão Joelho"),
            Map.entry("síndrome patelofemoral", "Lesão Joelho"),
            Map.entry("entorse", "Lesão tornozelo"),
            Map.entry("fascite plantar", "Lesão tornozelo"),
            Map.entry("tendinite de aquiles", "Lesão tornozelo"),
            Map.entry("tendinite do tendão de aquiles", "Lesão tornozelo"),
            Map.entry("epicondilite", "Lesão Cotovelo"),
            Map.entry("túnel cárpico", "Lesão Punho"),
            Map.entry("tunel carpico", "Lesão Punho")
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

    // =========================================================================
    // odes adicionar uma verificação programática no Java para garantir que a IA cumpriu a regra do rácio postural no dia UPPER.
    // Se a IA gerar mais exercícios de Peito do que de Costas para um utente com protração de ombros, o Java deteta e força um
    // =========================================================================

    public void validarProporcaoUpperVisbody(TrainingPlanResponse response, String reportVisbodyText) {
        if (response == null || response.getPlan() == null || reportVisbodyText == null) return;

        boolean temProtracaoOmbros = reportVisbodyText.toLowerCase().contains("protração")
                || reportVisbodyText.toLowerCase().contains("enrolados");

        if (!temProtracaoOmbros) return;

        for (TrainingDay day : response.getPlan()) {
            if (day.getDay() != null && day.getDay().toUpperCase().contains("UPPER")) {
                long exPeito = day.getExercises().stream()
                        .filter(e -> e.getMuscleGroup() != null && e.getMuscleGroup().equalsIgnoreCase("Peito"))
                        .count();

                long exCostas = day.getExercises().stream()
                        .filter(e -> e.getMuscleGroup() != null && (e.getMuscleGroup().equalsIgnoreCase("Costas") || e.getMuscleGroup().equalsIgnoreCase("Ombros")))
                        .count();

                if (exPeito > exCostas) {
                    log.warn("[BLOQUEIO POSTURAL] O dia UPPER contem {} exercicios de Peito e apenas {} de Costas/Ombros para utente com ombros enrolados.", exPeito, exCostas);
                    throw new IllegalArgumentException(
                            "VIOLAÇÃO DE CORREÇÃO POSTURAL: Utente apresenta ombros enrolados no VisBody. " +
                                    "O número de exercícios de Costas/Cadeia Posterior (" + exCostas + ") deve ser superior ao de Peito (" + exPeito + ")."
                    );
                }
            }
        }
    }
}