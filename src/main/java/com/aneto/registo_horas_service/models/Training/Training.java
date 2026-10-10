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
import java.util.stream.Collectors;

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
    private final TrainingPromptBuilder promptBuilder;
    private final ClinicalPromptBuilder clinicalPromptBuilder;
    private final ClinicalTrainingValidator clinicalTrainingValidator;

    private static final int MAX_RETRIES = 5;

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3) {
        return generateTrainingPlan(userRequest, exerciciosDoS3, 1);
    }

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3, int weekNumber) {

        boolean ehClinico = isPlanoClinico(userRequest);

        log.info("[TrainingPlan] Início da geração. Semana: {} | Modo Clínico Ativo? {}", weekNumber, ehClinico);

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

        StringBuilder pathologyBuilder = new StringBuilder(pathologyDeclarada);
        if (pathologyInferidaRelatorio != null && !pathologyInferidaRelatorio.isBlank()) {
            if (pathologyBuilder.toString().contains("Nenhuma")) {
                pathologyBuilder = new StringBuilder(pathologyInferidaRelatorio);
            } else {
                pathologyBuilder.append(", ").append(pathologyInferidaRelatorio);
            }
        }

        String pathologyText = pathologyBuilder.toString();
        boolean pathologyEspecifica = !pathologyText.contains("Nenhuma") || userRequest.getMedicalReportText() != null || userRequest.getReportVisbobyText() != null;

        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(), userRequest.getHeightCm(), userRequest.getAge(),
                genderText, bodyTypeText,
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 15.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 6
        );

        Map<String, List<String>> exerciseDictionary = carregarDicionario();
        Map<String, Map<String, List<String>>> exerciseDictionaryComSub = carregarDicionarioComSubcategoria(exerciseDictionary);

        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");

        // =========================================================================
        // 💡 APLICAÇÃO DA CONDIÇÃO DE PROMPT (CLÍNICO vs CONVENCIONAL)
        // =========================================================================
        String userPrompt;
        if (ehClinico) {
            // Usa o prompt clínico focado na estrutura de 4 blocos temporizados
            userPrompt = clinicalPromptBuilder.buildClinicalPrompt(userRequest, macros, exerciciosDoS3);
        } else {
            // Usa o prompt convencional (divisão Upper/Lower ou Foco Estético)
            userPrompt = promptBuilder.buildUserPrompt(
                    userRequest, weekNumber, isDeloadWeek, volumeIdeal, totalMinutos,
                    pathologyText, pathologyEspecifica, macros, exerciseDictionary,
                    exerciseDictionaryComSub, exerciciosDoS3,
                    isGluteFocus
            );
        }

        TrainingPlanResponse resultado = executeGeneration(
                userPrompt, totalMinutos, exerciseDictionary,
                pathologyText, exerciciosDoS3, pathologyEspecifica,
                userRequest.getWeightKg(), isGluteFocus, userRequest, ehClinico
        );

        resultado.setSummary(garantirFechoMotivacional(resultado.getSummary(), userRequest, isDeloadWeek, weekNumber));
        return resultado;
    }

    private TrainingPlanResponse executeGeneration(
            String prompt, int totalMinutos, Map<String, List<String>> exerciseDictionary,
            String pathologyText, List<String> exerciciosAnteriores, boolean pathologyEspecifica,
            Double weightKg, boolean aplicaFocoGluteoExtremo, UserProfileRequest userRequest, boolean ehClinico) {

        Set<String> validNames = flattenDictionary(exerciseDictionary);
        Set<String> nomesAnteriores = (exerciciosAnteriores == null ? List.<String>of() : exerciciosAnteriores).stream()
                .map(nome -> normalizeExerciseName(nome, validNames))
                .collect(Collectors.toSet());

        OpenAiChatOptions jsonModeOptions = OpenAiChatOptions.builder()
                .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();

        StringBuilder currentPrompt = new StringBuilder(prompt);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Set<String> nomesUsadosNestaTentativa = new HashSet<>();
            try {
                String promptSanitizado = sanitizarJsonDecimais(currentPrompt.toString());
                Prompt promptComJsonMode = new Prompt(new UserMessage(promptSanitizado), jsonModeOptions);

                ChatResponse chatResponse = chatModel.call(promptComJsonMode);
                log.info("[chatResponse]: {}", chatResponse);

                String cleanedJson = cleanMarkdown(chatResponse.getResult().getOutput().getContent());
                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // Validações
                trainingValidator.validarSequenciaDias(response.getPlan(), aplicaFocoGluteoExtremo);
                trainingValidator.validarSegurancaLombarEVisbody(
                        response,
                        userRequest.getMedicalReportText(),
                        userRequest.getReportVisbobyText()
                );
                trainingValidator.validarProporcaoUpperVisbody(
                        response,
                        userRequest.getReportVisbobyText()
                );

                // 1. Processa e enriquece o plano clássico ('plan') se existir
                List<TrainingDay> updatedPlan = new ArrayList<>();
                if (response.getPlan() != null) {
                    for (TrainingDay day : response.getPlan()) {
                        List<TrainingExercise> enrichedExercises = new ArrayList<>();
                        if (day.getExercises() != null) {
                            for (TrainingExercise ex : day.getExercises()) {
                                TrainingExercise enriched = trainingValidator.enrichExercise(ex, validNames, pathologyText);
                                nomesUsadosNestaTentativa.add(enriched.getName());
                                enrichedExercises.add(enriched);
                            }
                        }

                        List<TrainingExercise> exercisesComEstimativa = anexarEstimativaCalorica(enrichedExercises, exerciseDictionary, weightKg, totalMinutos);
                        updatedPlan.add(new TrainingDay(day.getDay(), exercisesComEstimativa));
                    }
                }

                // 2. Plano clínico multi-dias ('clinicalPlan'): enriquece com vídeos sem rejeitar nomes fora do dicionário
                if (ehClinico && response.getClinicalPlan() != null) {
                    for (ClinicalDay cDay : response.getClinicalPlan()) {
                        if (cDay.getBlocks() == null) continue;
                        for (ClinicalBlock block : cDay.getBlocks()) {
                            if (block.getExercises() == null) continue;
                            for (ClinicalExercise cEx : block.getExercises()) {
                                clinicalTrainingValidator.enriquecerExercicoComVideo(cEx);
                                nomesUsadosNestaTentativa.add(cEx.getName());
                            }
                        }
                    }
                }

                // 3. Processa e enriquece os blocos do PLANO CLÍNICO ('blocks') para incluir os links de vídeo S3
                if (ehClinico && response.getBlocks() != null) {
                    for (ClinicalBlock block : response.getBlocks()) {
                        if (block.getExercises() != null) {
                            block.getExercises().forEach(clinicalTrainingValidator::enriquecerExercicoComVideo);
                        }
                    }
                } else if (response.getBlocks() != null) {
                    for (ClinicalBlock block : response.getBlocks()) {
                        if (block.getExercises() != null) {
                            for (ClinicalExercise cEx : block.getExercises()) {
                                // Mapeia temporariamente para TrainingExercise para obter a URL do vídeo via S3/Dicionário
                                TrainingExercise tempEx = TrainingExercise.builder()
                                        .name(cEx.getName())
                                        .muscleGroup(cEx.getTargetFocus())
                                        .videoUrl(cEx.getVideoUrl())
                                        .build();

                                TrainingExercise enrichedTemp = trainingValidator.enrichExercise(tempEx, validNames, pathologyText);

                                // Atribui o videoUrl encontrado de volta ao ClinicalExercise
                                cEx.setVideoUrl(enrichedTemp.getVideoUrl());
                                nomesUsadosNestaTentativa.add(cEx.getName());
                            }
                        }
                    }
                }

                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .blocks(response.getBlocks()) // Os blocos agora contêm os videoUrls preenchidos!
                        .clinicalPlan(response.getClinicalPlan())
                        .primaryDiagnosis(response.getPrimaryDiagnosis())
                        .primaryObjective(response.getPrimaryObjective())
                        .validationStatus(response.getValidationStatus())
                        .safetyDirectives(response.getSafetyDirectives())
                        .dietPlan(response.getDietPlan())
                        .build();

            } catch (Exception e) {
                log.warn("[Generation Retry] Tentativa {}/{} falhou. Motivo: {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Falha crítica na geração do plano de treino após " + (MAX_RETRIES + 1) + " tentativas.", e);
                }
                currentPrompt.append(promptBuilder.buildFeedbackDeErro(e.getMessage(), nomesUsadosNestaTentativa, pathologyEspecifica, exerciseDictionary, nomesAnteriores));
            }
        }
        throw new RuntimeException("Falha inesperada na geração do plano.");
    }
    private Map<String, List<String>> carregarDicionario() {
        try {
            Map<String, List<String>> dict = exerciseVideoService.getExerciseDictionary();
            return (dict != null && !dict.isEmpty()) ? dict : FALLBACK_DICTIONARY;
        } catch (Exception e) {
            log.error("Erro ao carregar dicionário do servidor de vídeos. Utilizando fallback.", e);
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

    /**
     * Avalia se o pedido contém relatórios ou diagnósticos clínicos ativos
     * que exigem uma estrutura de treino em 4 blocos temporizados de reabilitação.
     */
    public boolean isPlanoClinico(UserProfileRequest userRequest) {
        if (userRequest == null) return false;

        boolean temRelatorioMedico = userRequest.getMedicalReportText() != null
                && !userRequest.getMedicalReportText().isBlank();

        boolean temRelatorioVisbody = userRequest.getReportVisbobyText() != null
                && !userRequest.getReportVisbobyText().isBlank();

        boolean temPatologiaDeclarada = userRequest.getPathology() != null
                && !userRequest.getPathology().isBlank()
                && !userRequest.getPathology().equalsIgnoreCase("Nenhuma")
                && !userRequest.getPathology().equalsIgnoreCase("Nenhuma limitação relatada");

        return temRelatorioMedico || temPatologiaDeclarada || (temRelatorioVisbody && contemDesviosClinicos(userRequest.getReportVisbobyText()));
    }

    private boolean contemDesviosClinicos(String visbodyText) {
        if (visbodyText == null || visbodyText.isBlank()) return false;
        String lower = visbodyText.toLowerCase();

        // Palavras-chave que indicam necessidade de intervenção clínica postural/neuromuscular
        return lower.contains("anteversão")
                || lower.contains("anteversao")
                || lower.contains("protração")
                || lower.contains("protracao")
                || lower.contains("assimetria")
                || lower.contains("hiperextensão")
                || lower.contains("hiperextensao")
                || lower.contains("défice")
                || lower.contains("defice")
                || lower.contains("sequestrado")
                || lower.contains("compressão")
                || lower.contains("compressao");
    }

    /**
     * Filtra o texto do prompt substituindo vírgulas entre dígitos por ponto (ex: 72,0 -> 72.0).
     * Evita que valores numéricos em exemplos de JSON quebrem a validação da OpenAI API.
     */
    private String sanitizarJsonDecimais(String text) {
        if (text == null) return "";
        return text.replaceAll("(?<=:\\s*\\d+),(\\d+)", ".$1");
    }
}