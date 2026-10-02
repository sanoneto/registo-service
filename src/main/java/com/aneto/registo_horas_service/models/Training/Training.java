package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.aneto.registo_horas_service.models.Training.TrainingRuleConstants.*;
import static com.aneto.registo_horas_service.models.Training.TrainingUtils.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class Training {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ExerciseVideoService exerciseVideoService;
    private final TrainingValidator trainingValidator;

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3) {
        return generateTrainingPlan(userRequest, exerciciosDoS3, 1);
    }

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3, int weekNumber) {

        boolean temRelatorioMedico = userRequest.getMedicalReportText() != null && !userRequest.getMedicalReportText().isBlank();
        boolean temRelatorioVisbody = userRequest.getReportVisbobyText() != null && !userRequest.getReportVisbobyText().isBlank();

        log.info("Iniciando generateTrainingPlan. Tem relatório médico? {} | Tem relatório VisBody? {}",
                temRelatorioMedico, temRelatorioVisbody);

        if (temRelatorioVisbody) {
            log.info("[generateTrainingPlan] CONTEÚDO VISBODY DETETADO (Tamanho: {} chars)", userRequest.getReportVisbobyText().length());
        } else {
            log.warn("[generateTrainingPlan] ATENÇÃO: reportVisbobyText veio NULL ou VAZIO do frontend!");
        }

        String objectiveText = defaultIfEmpty(userRequest.getObjective(), "Manutenção de saúde e bem-estar");
        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";

        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 minutos");
        int totalMinutos = extrairMinutosTotais(durationText);
        int volumeIdealBase = Math.max(6, totalMinutos / 7);

        boolean isDeloadWeek = weekNumber > 0 && weekNumber % 4 == 0;
        int volumeIdeal = isDeloadWeek ? Math.max(4, (int) Math.round(volumeIdealBase * 0.7)) : volumeIdealBase;

        String pathologyDeclarada = defaultIfEmpty(userRequest.getPathology(), "Nenhuma limitação relatada");

        // Inferência a partir do Relatório Médico
        PatologiaInferida patologiaInferida = trainingValidator.inferirPatologiaDoRelatorio(userRequest.getMedicalReportText());
        String pathologyInferidaRelatorio = (patologiaInferida == null) ? null : patologiaInferida.categorias();

        // Construção do texto consolidado de Patologias / Postura
        StringBuilder pathologyBuilder = new StringBuilder(pathologyDeclarada);
        if (pathologyInferidaRelatorio != null && !pathologyInferidaRelatorio.isBlank()) {
            if (pathologyBuilder.toString().contains("Nenhuma")) {
                pathologyBuilder = new StringBuilder(pathologyInferidaRelatorio);
            } else {
                pathologyBuilder.append(", ").append(pathologyInferidaRelatorio);
            }
        }

        String pathologyText = pathologyBuilder.toString();
        boolean pathologyEspecifica = !pathologyText.contains("Nenhuma") || temRelatorioMedico || temRelatorioVisbody;

        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(), userRequest.getHeightCm(), userRequest.getAge(),
                genderText, bodyTypeText,
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 15.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 6
        );

        Map<String, List<String>> exerciseDictionary = carregarDicionario();
        Map<String, Map<String, List<String>>> exerciseDictionaryComSub = carregarDicionarioComSubcategoria(exerciseDictionary);

        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");
        boolean aplicaFocoGluteoExtremo = isGluteFocus;

        TrainingPromptBuilder promptBuilder = new TrainingPromptBuilder();
        String userPrompt = promptBuilder.buildUserPrompt(
                userRequest, weekNumber, isDeloadWeek, volumeIdeal, totalMinutos,
                pathologyText, pathologyEspecifica, macros, exerciseDictionary,
                exerciseDictionaryComSub, exerciciosDoS3,
                aplicaFocoGluteoExtremo
        );

        TrainingPlanResponse resultado = executeGeneration(
                userPrompt, totalMinutos, exerciseDictionary, exerciseDictionaryComSub,
                pathologyText, exerciciosDoS3, pathologyEspecifica,
                isObjetivoCompativel(objectiveText), userRequest.getWeightKg(),
                aplicaFocoGluteoExtremo, promptBuilder, userRequest
        );

        resultado.setSummary(garantirFechoMotivacional(resultado.getSummary(), userRequest, isDeloadWeek, weekNumber));
        return resultado;
    }

    private TrainingPlanResponse executeGeneration(
            String prompt, int totalMinutos, Map<String, List<String>> exerciseDictionary,
            Map<String, Map<String, List<String>>> exerciseDictionaryComSub,
            String pathologyText, List<String> exerciciosAnteriores, boolean pathologyEspecifica,
            boolean permiteFinalizador, Double weightKg, boolean aplicaFocoGluteoExtremo,
            TrainingPromptBuilder promptBuilder, UserProfileRequest userRequest) {

        int maxRetries = 8;
        Set<String> validNames = flattenDictionary(exerciseDictionary);
        Set<String> nomesAnteriores = (exerciciosAnteriores == null ? List.<String>of() : exerciciosAnteriores).stream()
                .map(nome -> normalizeExerciseName(nome, validNames))
                .collect(java.util.stream.Collectors.toSet());

        OpenAiChatOptions jsonModeOptions = OpenAiChatOptions.builder()
                .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();

        StringBuilder currentPrompt = new StringBuilder(prompt);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Set<String> nomesUsadosNestaTentativa = new HashSet<>();
            try {
                Prompt promptComJsonMode = new Prompt(new UserMessage(currentPrompt.toString()), jsonModeOptions);
                log.info("prompt -> {}", promptComJsonMode);
                ChatResponse chatResponse = chatModel.call(promptComJsonMode);
                String cleanedJson = cleanMarkdown(chatResponse.getResult().getOutput().getContent());

                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // 1. Validação da sequência de dias
                trainingValidator.validarSequenciaDias(response.getPlan(), aplicaFocoGluteoExtremo);

                // 2. Validação Rígida de Segurança Médica e VisBody (Lança excepção se violar)
                trainingValidator.validarSegurancaLombarEVisbody(
                        response,
                        userRequest.getMedicalReportText(),
                        userRequest.getReportVisbobyText()
                );

                List<TrainingDay> updatedPlan = new ArrayList<>();
                for (TrainingDay day : response.getPlan()) {
                    List<TrainingExercise> enrichedExercises = new ArrayList<>();
                    for (TrainingExercise ex : day.getExercises()) {
                        TrainingExercise enriched = trainingValidator.enrichExercise(ex, validNames, pathologyText);
                        nomesUsadosNestaTentativa.add(enriched.getName());
                        enrichedExercises.add(enriched);
                    }

                    List<TrainingExercise> exercisesComEstimativa = anexarEstimativaCalorica(enrichedExercises, exerciseDictionary, weightKg, totalMinutos);
                    updatedPlan.add(new TrainingDay(day.getDay(), exercisesComEstimativa));
                }

                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .dietPlan(response.getDietPlan())
                        .build();

            } catch (Exception e) {
                log.warn("Falha na tentativa {}/{} - Erro: {}", attempt, maxRetries, e.getMessage());
                if (attempt >= maxRetries) throw new RuntimeException("Falha crítica na geração do plano após várias tentativas.");
                currentPrompt.append(promptBuilder.buildFeedbackDeErro(e.getMessage(), nomesUsadosNestaTentativa, pathologyEspecifica, exerciseDictionary, nomesAnteriores));
            }
        }
        throw new RuntimeException("Falha na geração do plano.");
    }

    private Map<String, List<String>> carregarDicionario() {
        try {
            Map<String, List<String>> dict = exerciseVideoService.getExerciseDictionary();
            return (dict != null && !dict.isEmpty()) ? dict : FALLBACK_DICTIONARY;
        } catch (Exception e) {
            return FALLBACK_DICTIONARY;
        }
    }

    private Map<String, Map<String, List<String>>> carregarDicionarioComSubcategoria(Map<String, List<String>> fallback) {
        try {
            Map<String, Map<String, List<String>>> dictComSub = exerciseVideoService.getExerciseDictionaryComSubcategoria();
            return (dictComSub != null && !dictComSub.isEmpty()) ? dictComSub : envolverSemSubcategoria(fallback);
        } catch (Exception e) {
            return envolverSemSubcategoria(fallback);
        }
    }
}