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
import java.util.function.BiFunction;
import java.util.function.Function;
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


    private static final int MAX_RETRIES = 5;

    private static final List<String> DESVIOS_CLINICOS_KEYWORDS = List.of(
            "anteversão", "anteversao",
            "protração", "protracao",
            "assimetria",
            "hiperextensão", "hiperextensao",
            "défice", "defice",
            "sequestrado",
            "compressão", "compressao"
    );

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3, int weekNumber) {

        boolean ehClinico = isPlanoClinico(userRequest);

        log.info("[TrainingPlan] Início da geração. Semana: {} | Modo Clínico Ativo? {}", weekNumber, ehClinico);

        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";

        String durationText = defaultIfEmpty(userRequest.getDurationPerSession(), "60 minutos");
        int totalMinutos = extrairMinutosTotais(durationText);
        int volumeIdealBase = Math.max(6, totalMinutos / 7);

        boolean isDeloadWeek = weekNumber > 0 && weekNumber % 4 == 0;
        int volumeIdeal = isDeloadWeek ? Math.max(4, (int) Math.round(volumeIdealBase * 0.7)) : volumeIdealBase;

        String pathologyText = resolverPatologiaCompleta(userRequest);
        boolean pathologyEspecifica = !pathologyText.contains("Nenhuma")
                || userRequest.getMedicalReportText() != null
                || userRequest.getReportVisbobyText() != null;

        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(), userRequest.getHeightCm(), userRequest.getAge(),
                genderText, bodyTypeText,
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 15.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 6
        );

        Map<String, List<String>> exerciseDictionary = carregarDicionario();
        Map<String, Map<String, List<String>>> exerciseDictionaryComSub = carregarDicionarioComSubcategoria(exerciseDictionary);

        boolean isGluteFocus = isFocoGluteo(userRequest.getObjective());

        String userPrompt = ehClinico
                ? clinicalPromptBuilder.buildClinicalPrompt(userRequest, macros, exerciciosDoS3, volumeIdeal)
                : promptBuilder.buildUserPrompt(
                userRequest, weekNumber, isDeloadWeek, volumeIdeal, totalMinutos,
                pathologyText, pathologyEspecifica, macros, exerciseDictionary,
                exerciseDictionaryComSub, exerciciosDoS3,
                isGluteFocus
        );

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
                log.debug("[chatResponse]: {}", chatResponse);

                String cleanedJson = cleanMarkdown(chatResponse.getResult().getOutput().getContent());
                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // Validações específicas por modo (Clínico vs Convencional)
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

                // Processa e enriquece 'plan' sem duplicação de código
                List<TrainingDay> updatedPlan = enriquecerListaDeDias(
                        response.getPlan(),
                        TrainingDay::getDay,
                        TrainingDay::getExercises,
                        TrainingDay::new,
                        validNames,
                        pathologyText,
                        exerciseDictionary,
                        weightKg,
                        totalMinutos,
                        nomesUsadosNestaTentativa
                );

                // Processa e enriquece 'clinicalPlan' reutilizando exatamente a mesma lógica
                List<ClinicalDay> updatedClinicalPlan = enriquecerListaDeDias(
                        response.getClinicalPlan(),
                        ClinicalDay::getDay,
                        ClinicalDay::getExercises,
                        ClinicalDay::new,
                        validNames,
                        pathologyText,
                        exerciseDictionary,
                        weightKg,
                        totalMinutos,
                        nomesUsadosNestaTentativa
                );

                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .userProfile(userRequest)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .clinicalPlan(updatedClinicalPlan)
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
                currentPrompt.append(promptBuilder.buildFeedbackDeErro(
                        e.getMessage(),
                        nomesUsadosNestaTentativa,
                        pathologyEspecifica,
                        exerciseDictionary,
                        nomesAnteriores
                ));
            }
        }
        throw new RuntimeException("Falha inesperada na geração do plano.");
    }

    /**
     * Elimina a duplicação entre o processamento de TrainingDay e ClinicalDay,
     * enriquecendo cada exercício com metadados/vídeos e anexando a estimativa calórica.
     */
    private <T> List<T> enriquecerListaDeDias(
            List<T> dias,
            Function<T, String> dayNameExtractor,
            Function<T, List<TrainingExercise>> exercisesExtractor,
            BiFunction<String, List<TrainingExercise>, T> dayConstructor,
            Set<String> validNames,
            String pathologyText,
            Map<String, List<String>> exerciseDictionary,
            Double weightKg,
            int totalMinutos,
            Set<String> nomesUsadosNestaTentativa) {

        if (dias == null || dias.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> updatedDays = new ArrayList<>(dias.size());
        for (T day : dias) {
            List<TrainingExercise> rawExercises = exercisesExtractor.apply(day);
            List<TrainingExercise> enrichedExercises = new ArrayList<>();

            if (rawExercises != null) {
                for (TrainingExercise ex : rawExercises) {
                    TrainingExercise enriched = trainingValidator.enrichExercise(ex, validNames, pathologyText);
                    nomesUsadosNestaTentativa.add(enriched.getName());
                    enrichedExercises.add(enriched);
                }
            }

            List<TrainingExercise> exercisesComEstimativa = anexarEstimativaCalorica(
                    enrichedExercises, exerciseDictionary, weightKg, totalMinutos
            );
            updatedDays.add(dayConstructor.apply(dayNameExtractor.apply(day), exercisesComEstimativa));
        }
        return updatedDays;
    }

    private String resolverPatologiaCompleta(UserProfileRequest userRequest) {
        String pathologyDeclarada = defaultIfEmpty(userRequest.getPathology(), "Nenhuma limitação relatada");
        PatologiaInferida patologiaInferida = trainingValidator.inferirPatologiaDoRelatorio(userRequest.getMedicalReportText());
        String pathologyInferidaRelatorio = (patologiaInferida == null) ? null : patologiaInferida.categorias();

        if (pathologyInferidaRelatorio == null || pathologyInferidaRelatorio.isBlank()) {
            return pathologyDeclarada;
        }
        if (pathologyDeclarada.contains("Nenhuma")) {
            return pathologyInferidaRelatorio;
        }
        return pathologyDeclarada + ", " + pathologyInferidaRelatorio;
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
     * que exigem uma estrutura de treino clínica de reabilitação.
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

        return temRelatorioMedico
                || temPatologiaDeclarada
                || (temRelatorioVisbody && contemDesviosClinicos(userRequest.getReportVisbobyText()));
    }

    private boolean contemDesviosClinicos(String visbodyText) {
        if (visbodyText == null || visbodyText.isBlank()) return false;
        String lower = visbodyText.toLowerCase();
        return DESVIOS_CLINICOS_KEYWORDS.stream().anyMatch(lower::contains);
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