package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.EstadoPedido;
import com.aneto.registo_horas_service.models.EstadoPlano;
import com.aneto.registo_horas_service.service.PlanoService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {
    private static final Logger log = LoggerFactory.getLogger(TrainingPlanServiceImpl.class);

    private final ObjectMapper objectMapper;
    private final GenerativeModel generativeModel;
    private final PlanoService planoService;

    private final S3Client s3Client;
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.folder-name}")
    private String S3FOLDER;

    @Override
    public TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username, String planId) {
        // 1. Determina a chave (prioriza a existente no banco, senão usa o padrão)
        String key = buscarChaveDoPlano(planId, username, request);
        // 2. Se o request for insuficiente para gerar algo novo, tenta carregar do S3
        if (isRequestEmpty(request)) {
            return loadFromS3(key)
                    .orElseThrow(() -> new RuntimeException("Dados insuficientes para gerar novo plano e nenhum histórico encontrado."));
        }
        // 3. Se chegou aqui, temos dados para gerar um novo plano
        TrainingPlanResponse newPlan = generateTrainingPlan(request);
        configurarNovoPlano(newPlan, request);
        // 4. Persistência (Banco e S3)
        salvarDadosDoPlano(username, request, key, newPlan, planId, false);
        return newPlan;
    }

    @Override
    public void updatePlan(TrainingPlanResponse newPlan, String username, String planId) {
        String key = buscarChaveDoPlano(planId, username, null);
        configurarNovoPlano(newPlan, newPlan.getUserProfile());
        // 4. Persistência (Banco e S3)
        salvarDadosDoPlano(username, newPlan.getUserProfile(), key, newPlan, planId, true);
    }

    @Override
    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {
        String userPrompt = """
                Atue como um Personal Trainer e Nutricionista especialista em reabilitação física.
                Gere um plano de treino e um plano alimentar personalizado de %d dias por semana em Português.
                
                PERFIL DO ALUNO:
                - Biótipo: %s
                - Género: %s
                - Altura: %.2f cm
                - Peso: %.2f kg
                - Histórico de Exercício: %s
                - Patologias/Limitações: %s
                - Localização: %s (País: %s)
                - Objetivo: %s
                
                DIRETRIZES DE TREINO (ADAPTAÇÃO ESPECÍFICA):
                1. O plano deve ser dividido EXATAMENTE em %d dias.
                2. ADAPTAÇÃO PARA LIMITAÇÕES:
                   - Analise a limitação mencionada ("%s") e adapte todos os exercícios para evitar o agravamento da dor.
                   - Substitua exercícios de risco por alternativas seguras.
                   - Inclua exercícios de mobilidade ou fortalecimento específicos para a zona afetada: %s.
                
                DIRETRIZES DE NUTRIÇÃO:
                1. Calcule o gasto calórico diário estimado para o objetivo de %s.
                2. Sugira alimentos típicos e acessíveis na região de %s, %s.
                3. Se a limitação for inflamatória, priorize alimentos com propriedades anti-inflamatórias.
                
                FORMATO DE RESPOSTA (JSON APENAS):
                {
                  "summary": "Explicação da estratégia de treino focada na adaptação para %s e nutrição para o objetivo.",
                  "plan": [
                    {
                      "day": "Dia X - [Foco do Treino]",
                      "exercises": [
                        {
                          "name": "Nome do Exercício",
                          "muscleGroup": "Ex: Peito, Costas, Quadríceps",
                          "sets": "Séries",
                          "reps": "Repetições",
                          "rest": "Tempo de descanso",
                          "details": "Dica de segurança focada em %s",
                          "notas": "Instrução técnica"
                        }
                      ]
                    }
                  ],
                  "dietPlan": {
                    "dailyCalories": 2500,
                    "macroDistribution": {
                      "protein": "Xg",
                      "carbs": "Xg",
                      "fats": "Xg"
                    },
                    "meals": [
                      {
                        "time": "HH:MM",
                        "description": "Nome da Refeição",
                        "ingredients": ["Ingrediente 1", "Ingrediente 2"]
                      }
                    ],
                    "localTips": "Dicas regionais."
                  }
                }
                """.formatted(
                userRequest.frequencyPerWeek(), // %d dias/semana
                userRequest.bodyType(),         // %s Biótipo
                userRequest.gender(),           // %s Género
                userRequest.heightCm(),         // %.2f Altura
                userRequest.weightKg(),         // %.2f Peso
                userRequest.exerciseHistory(),  // %s Histórico
                userRequest.pathology(),        // %s Patologias
                userRequest.location(),         // %s Cidade (Localização)
                userRequest.country(),          // %s País
                userRequest.objective(),        // %s Objetivo

                userRequest.frequencyPerWeek(), // %d diretrizes treino
                userRequest.pathology(),        // %s Analise a limitação
                userRequest.pathology(),        // %s Fortalecimento zona

                userRequest.objective(),        // %s Gasto calórico
                userRequest.location(),         // %s Sugira alimentos região
                userRequest.country(),          // %s País (Nutrição)

                userRequest.pathology(),        // %s Sumário JSON
                userRequest.pathology()         // %s Detalhes exercício JSON
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
            //  if (Duration.between(lastModified, Instant.now()).toDays() > 7) {
            //   return Optional.empty();
            //  }
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

    // --- Métodos Auxiliares para Limpar o Fluxo Principal ---
    private String buscarChaveDoPlano(String planId, String username, UserProfileRequest request) {
        // 1. Tentativa prioritária: Pelo UUID do plano (planId)
        if (planId != null && !planId.isBlank()) {
            try {
                PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
                if (plano != null && isLinkValido(plano.link())) {
                    return plano.link();
                }
            } catch (IllegalArgumentException e) {
                log.error("ID do plano inválido: {}", planId);
            }
        }

        // 2. Validação na Base de Dados: Procurar plano existente por Username + Filtros
        // O service deve buscar onde estado_plano = 'ATIVO' e estado_pedido = 'FINALIZADO'
        log.info(" Aprocura plano ativo e concluído na base para o utilizador: {}", username);
        if (request == null) {
            return planoService.findAtivoAndConcluidoByUsername(username)
                    .map(PlanoResponseDTO::link)
                    .filter(this::isLinkValido)
                    // 3. Fallback Final: Se não encontrar nada válido, gera o caminho padrão
                    .orElseGet(() -> {
                        log.info("Nenhum plano ativo encontrado para {}. Gerando fallback.", username);
                        return gerarCaminhoPadrao(username);
                    });
        } else {
            return gerarCaminhoPadrao(username);
        }
    }

    private boolean isLinkValido(String link) {
        return link != null && !link.isBlank();
    }

    // Criado um método auxiliar para evitar repetição de código
    private String gerarCaminhoPadrao(String username) {
        // Define o formato: AnoMesDia_HoraMinutoSegundo
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // O caminho incluirá o username e o momento da criação
        return String.format("%s%s/plan/%s_%s.json", S3FOLDER, username, username, timestamp);
    }

    private boolean isRequestEmpty(UserProfileRequest request) {
        return request == null || (request.bodyType() == null && request.objective() == null);
    }

    private void configurarNovoPlano(TrainingPlanResponse plan, UserProfileRequest request) {
        plan.setIsExistingPlan(true); // Se entendi, isso marca que o arquivo passará a existir
        plan.setUserProfile(request);
    }

    private void salvarDadosDoPlano(String username, UserProfileRequest request, String key, TrainingPlanResponse plan, String planId, boolean update) {
        PlanoRequestDTO dto;

        if (update) {
            planoService.prepararNovoPlanoAtivo(username);
        }

        if (planId == null || planId.isEmpty()) {
            // --- OTIMIZAÇÃO AQUI ---
            // Verificamos uma única vez se existe um nome de aluno no request
            boolean temNomeAluno = request.studentName() != null && !request.studentName().isBlank();

            String nomeNoPlano = temNomeAluno ? request.studentName() : username;
            String especialista = temNomeAluno ? username : "Sem Especialista";
            String recommended = temNomeAluno ? request.recommended() : username;

            dto = new PlanoRequestDTO(
                    nomeNoPlano,
                    request.objective(),
                    especialista,
                    EstadoPlano.ATIVO,
                    EstadoPedido.PENDENTE,
                    key,
                    recommended
            );
            planoService.createPlano(dto);
            // -----------------------
        } else {
            PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));

            if (planoExistente == null) {
                throw new RuntimeException("Plano não encontrado para o ID: " + planId);
            }

            dto = new PlanoRequestDTO(
                    planoExistente.nomeAluno(),
                    planoExistente.objetivo(),
                    username,
                    EstadoPlano.ATIVO,
                    EstadoPedido.FINALIZADO,
                    key,
                    planoExistente.recommended()
            );
            planoService.updatePlano(planId, dto);
        }

        saveToS3(key, plan);
    }
}