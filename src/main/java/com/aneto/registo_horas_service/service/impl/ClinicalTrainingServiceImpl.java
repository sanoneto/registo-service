package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ClinicalBlock;
import com.aneto.registo_horas_service.dto.response.ClinicalDay;
import com.aneto.registo_horas_service.dto.response.Macros;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.Training.ClinicalPromptBuilder;
import com.aneto.registo_horas_service.models.Training.ClinicalTrainingValidator;
import com.aneto.registo_horas_service.models.Training.MacroCalculator;
import com.aneto.registo_horas_service.service.ClinicalTrainingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.aneto.registo_horas_service.models.Training.TrainingUtils.cleanMarkdown;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClinicalTrainingServiceImpl implements ClinicalTrainingService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ClinicalPromptBuilder clinicalPromptBuilder;
    private final ClinicalTrainingValidator clinicalTrainingValidator;

    private static final int MAX_RETRIES = 5;

    @Override
    public TrainingPlanResponse generateClinicalPlan(UserProfileRequest userRequest, List<String> exerciciosAnteriores) {
        log.info("[ClinicalTrainingService] A iniciar geração de plano clínico multi-dias em blocos para utente: {}", userRequest.getStudentName());

        // 1. Cálculo ajustado de Macros e Caloria base (Mifflin-St Jeor)
        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(),
                userRequest.getHeightCm(),
                userRequest.getAge(),
                userRequest.getGender() != null ? userRequest.getGender().name() : "MALE",
                userRequest.getBodyType() != null ? userRequest.getBodyType().name() : "ECTOMORPH",
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 20.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 4
        );

        // 2. Construção do Prompt Clínico Multi-Dias
        String promptText = clinicalPromptBuilder.buildClinicalPrompt(userRequest, macros, exerciciosAnteriores);

        OpenAiChatOptions jsonOptions = OpenAiChatOptions.builder()
                .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();

        StringBuilder currentPrompt = new StringBuilder(promptText);

        // 3. Ciclo de Execução, Validação e Enriquecimento
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Prompt prompt = new Prompt(new UserMessage(currentPrompt.toString()), jsonOptions);
                ChatResponse response = chatModel.call(prompt);
                String rawJson = cleanMarkdown(response.getResult().getOutput().getContent());

                TrainingPlanResponse clinicalPlan = objectMapper.readValue(rawJson, TrainingPlanResponse.class);

                // Validação de Segurança Clínica e Estrutura dos Dias
                clinicalTrainingValidator.validarPlanoClinico(
                        clinicalPlan,
                        userRequest.getMedicalReportText(),
                        userRequest.getReportVisbobyText()
                );

                // Enriquecimento dos exercícios com links de vídeo S3 percorrendo cada dia e cada bloco
                List<ClinicalDay> clinicalDays = clinicalPlan.getClinicalPlan();
                if (clinicalDays != null && !clinicalDays.isEmpty()) {
                    for (ClinicalDay day : clinicalDays) {
                        if (day.getBlocks() != null) {
                            for (ClinicalBlock block : day.getBlocks()) {
                                if (block.getExercises() != null) {
                                    block.getExercises().forEach(clinicalTrainingValidator::enriquecerExercicoComVideo);
                                }
                            }
                        }
                    }
                }

                log.info("[ClinicalTrainingService] Plano clínico multi-dias gerado e enriquecido com sucesso.");
                return clinicalPlan;

            } catch (Exception e) {
                log.warn("[Clinical Retry] Tentativa {}/{} falhou. Motivo: {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Falha crítica na geração do plano de treino clínico em blocos após várias tentativas.", e);
                }
                currentPrompt.append("\nERRO NA TENTATIVA ANTERIOR: ").append(e.getMessage()).append("\nCORRIGE E GERA NOVAMENTE.");
            }
        }

        throw new RuntimeException("Falha ao gerar o plano clínico.");
    }
}