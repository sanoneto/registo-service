package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {
    private static final Logger log = LoggerFactory.getLogger(TrainingPlanServiceImpl.class);

    private final ObjectMapper objectMapper;
    private final GenerativeModel generativeModel;

    private final S3Client s3Client;
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.folder-name}")
    private String S3FOLDER;

    @Override
    public TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username) {
        String key = S3FOLDER + username + "/plan/" + username + ".json";

        boolean isRequestEmpty = (request == null) || (request.bodyType() == null && request.objective() == null);

        if (isRequestEmpty) {
            // Tentar buscar plano existente se não foram enviados dados novos
            Optional<TrainingPlanResponse> existingPlan = loadFromS3(key);

            if (existingPlan.isPresent()) {
                // Retornamos o plano marcado como existente
                return existingPlan.get();
            } else {
                // Se o request é vazio e não há nada no S3, não podemos fazer nada
                throw new RuntimeException("Nenhum plano encontrado e dados insuficientes para gerar um novo.");
            }
        }

        // 2. Se não existir ou for antigo, gera novo
        TrainingPlanResponse newPlan = generateTrainingPlan(request);
        //guarda os input e true é para depois o escrever que o plano ja existe
        newPlan.setIsExistingPlan(true);
        newPlan.setUserProfile(request);
        // 3. Salva na Cloud (aqui o isExistingPlan irá como null ou false conforme o JSON gerado)
        saveToS3(key, newPlan);

        return newPlan;
    }

    @Override
    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {
        String userPrompt = """
                Atue como um Personal Trainer e Nutricionista especialista.
                Gere um plano de treino e um plano alimentar personalizado de %d dias por semana em Português.
                
                PERFIL DO ALUNO:
                - Biótipo: %s
                - Género: %s
                - Altura: %.2f cm
                - Peso: %.2f kg
                - Histórico de Exercício: %s
                - Patologias/Limitações: %s
                - Localização: %s, %s (País: %s)
                - Objetivo: %s
                
                DIRETRIZES DE TREINO:
                1. Adapte os exercícios considerando as patologias mencionadas.
                2. O plano deve ser dividido exatamente em %d dias.
                
                DIRETRIZES DE NUTRIÇÃO:
                1. Calcule o gasto calórico diário estimado para o objetivo de %s.
                2. Sugira alimentos típicos e acessíveis na região de %s, %s.
                3. Distribua as refeições ao longo do dia com foco em macronutrientes adequados ao biótipo %s.
                
                FORMATO DE RESPOSTA (JSON APENAS):
                {
                  "summary": "Explicação da estratégia de treino e nutrição focada no biótipo e limitações.",
                  "plan": [
                    {
                      "day": "Dia 1 - [Foco do Treino]",
                      "exercises": [
                        {
                          "name": "Nome do Exercício",
                          "sets": "Séries",
                          "reps": "Repetições",
                          "rest": "Tempo de descanso",
                          "details": "Dica de segurança"
                        }
                      ]
                    }
                  ],
                  "dietPlan": {
                    "dailyCalories": 2500,
                    "macroDistribution": {
                      "protein": "180g",
                      "carbs": "300g",
                      "fats": "70g"
                    },
                    "meals": [
                      {
                        "time": "08:00",
                        "description": "Pequeno-almoço/Café da manhã",
                        "ingredients": ["Ovos", "Pão integral", "Fruta local"]
                      }
                    ],
                    "localTips": "Breve comentário sobre alimentos específicos da região sugeridos."
                  }
                }
                """.formatted(
                userRequest.frequencyPerWeek(),
                userRequest.bodyType(),
                userRequest.gender(),
                userRequest.heightCm(),
                userRequest.weightKg(),
                userRequest.exerciseHistory(),
                userRequest.pathology(),
                userRequest.location(), userRequest.country(), userRequest.country(), // Localização e País
                userRequest.objective(),
                userRequest.frequencyPerWeek(),
                userRequest.objective(),
                userRequest.location(), userRequest.country(), // Para a nutrição local
                userRequest.bodyType()
        );

        Content content = Content.newBuilder()
                .addParts(Part.newBuilder()
                        .setText(userPrompt))
                .setRole("user")
                .build();

        try {
            GenerateContentResponse response = generativeModel.generateContent(content);
            String textResponse = ResponseHandler.getText(response);
            return objectMapper.readValue(cleanMarkdown(textResponse), TrainingPlanResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro Gemini: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<TrainingPlanResponse> loadFromS3(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);

            // Verifica se o plano tem menos de 7 dias (opcional)
            Instant lastModified = s3Object.response().lastModified();
            if (Duration.between(lastModified, Instant.now()).toDays() > 7) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(s3Object, TrainingPlanResponse.class));
        } catch (Exception e) {
            return Optional.empty(); // Arquivo não existe ou erro na leitura
        }
    }

    @Override
    public void saveToS3(String key, TrainingPlanResponse plan) {
        try {
            String json = objectMapper.writeValueAsString(plan);
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    RequestBody.fromString(json));
        } catch (Exception e) {
            log.error("Erro ao salvar no S3", e);
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