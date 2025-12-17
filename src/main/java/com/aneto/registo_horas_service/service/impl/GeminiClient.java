package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Service
public class GeminiClient {

    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private final ObjectMapper objectMapper;
    private final GenerativeModel generativeModel;

    public GeminiClient(ObjectMapper objectMapper,
                        @Value("${gcp.project.id}") String projectId,
                        @Value("${gcp.project.location}") String location,
                        @Value("${gcp.credentials.json-content}") String jsonContent) throws IOException {

        this.objectMapper = objectMapper;
// Antes de criar o stream, force a correção dos escapes de nova linha
        String fixedJson = jsonContent.replace("\\\\n", "\\n");

        GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(fixedJson.getBytes(StandardCharsets.UTF_8))
                )
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));

        // ADICIONE ESTA LINHA PARA DEBUG
        System.out.println("DEBUG - Account: " + ((ServiceAccountCredentials) credentials).getClientEmail());
        System.out.println("DEBUG - Project ID: " + ((ServiceAccountCredentials) credentials).getProjectId());

        // 2. Construir o VertexAI
        VertexAI vertexAI = new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .setCredentials(credentials)
                .build();

        // 3. Configuração de geração
        GenerationConfig config = GenerationConfig.newBuilder()
                .setTemperature(0.2f)
                .build();

        // 4. USAR O BUILDER PARA O GENERATIVEMODEL (Remove o aviso de deprecated)
        this.generativeModel = new GenerativeModel.Builder()
                .setModelName(GEMINI_MODEL)
                .setVertexAi(vertexAI)
                .setGenerationConfig(config)
                .build();
    }

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {
        String userPrompt = """
                Atue como um Personal Trainer especialista.
                Gere um plano de treino personalizado de %d dias por semana em Português.
                
                PERFIL DO ALUNO:
                - Biótipo: %s
                - Género: %s
                - Altura: %.2f cm
                - Peso: %.2f kg
                - Histórico de Exercício: %s
                - Patologias/Limitações: %s
                
                DIRETRIZES:
                1. Adapte os exercícios considerando as patologias mencionadas.
                2. O plano deve ser dividido exatamente em %d dias.
                3. O objetivo do treino é %s
                
                FORMATO DE RESPOSTA (JSON APENAS):
                {
                  "summary": "Breve explicação da estratégia de treino focada no biótipo e limitações.",
                  "plan": [
                    {
                      "day": "Dia 1 - [Foco do Treino]",
                      "exercises": [
                        {
                          "name": "Nome do Exercício",
                          "sets": "Séries",
                          "reps": "Repetições",
                          "rest": "Tempo de descanso",
                          "details": "Dica de execução ou observação de segurança"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                userRequest.frequencyPerWeek(), // Dias no texto
                userRequest.bodyType(),
                userRequest.gender(),
                userRequest.heightCm(),
                userRequest.weightKg(),
                userRequest.exerciseHistory(),
                userRequest.pathology(),
                userRequest.frequencyPerWeek(), // Dias para a regra
                userRequest.objective()
        );

        Content content = Content.newBuilder()
                .addParts(Part.newBuilder()
                        .setText(userPrompt))
                .setRole("user")
                .build();

        try {
            // Chamada limpa
            GenerateContentResponse response = generativeModel.generateContent(content);
            String textResponse = ResponseHandler.getText(response);
            return objectMapper.readValue(cleanMarkdown(textResponse), TrainingPlanResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro Gemini: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null) return "{}";
        return text.trim()
                .replaceAll("^```json", "")
                .replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();
    }
}