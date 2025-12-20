package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import com.aneto.registo_horas_service.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.GenerateContentResponse;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private final ObjectMapper objectMapper;
    private final GenerativeModel generativeModel;

    private final S3Client s3Client;
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.folder-name}")
    private String S3FOLDER;


    @Override
    public ListJogosResponse getListJogo(String username) {
        String key = S3FOLDER + username + "/jogos/" + username + ".json";

        // Tentar buscar plano existente se não foram enviados dados novos
        Optional<ListJogosResponse> existingPlan = loadFromS3jogos(key);

        if (existingPlan.isPresent()) {
            // Retornamos o plano marcado como existente
            return existingPlan.get();
        }
        // 2. Se não existir ou for antigo, gera novo
        ListJogosResponse newPlan = createtListJogo(username);
        //guarda os input e true é para depois o escrever que o plano ja existe
        saveToS3(key, newPlan);

        return newPlan;
    }

    @Override
    public ListJogosResponse createtListJogo(String username) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataFim = hoje.plusDays(3);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        String intervaloDatas = String.format("de %s até %s", hoje.format(formatter), dataFim.format(formatter));

        // Prompt otimizado para pesquisa real
        String userPrompt = String.format("""
                Tarefa: Listar obrigatoriamente os jogos de futebol das ligas: Liga Portugal, Premier League, La Liga, Ligue 1 e Bundesliga.
                Datas: %s.
                
                CANAIS OBRIGATÓRIOS (Atribua o mais provável se não tiver a certeza):
                - Sport TV (1 a 7)
                - DAZN (1 a 5)
                - BTV 1
                
                INSTRUÇÕES DE PREENCHIMENTO:
                1. Se souberes que há um jogo (ex: Benfica, Porto, Sporting, Real Madrid, Man City), mas não tiveres o canal exato, escreve "Sport TV / DAZN".
                2. NÃO DEIXES as listas de jogos vazias. Se houver jogos nestas ligas nestes dias, eles TÊM de aparecer.
                3. valide todos os resultado antes de devolver.
                5. Responde APENAS o JSON puro.
                
                FORMATO:
                {
                  "dias": [
                    {
                      "data": "YYYY-MM-DD",
                      "jogos": [
                        { "liga": "...", "equipa_casa": "...", "equipa_fora": "...", "hora": "HH:MM", "canal": "..." }
                      ]
                    }
                  ]
                }
                """, intervaloDatas);
        try {
            GenerateContentResponse response = generativeModel.generateContent(userPrompt);
            String textResponse = ResponseHandler.getText(response);

            // 1. Usa o método cleanMarkdown que já criaste!
            String jsonClean = cleanMarkdown(textResponse);

            // 2. Log para depuração (vê na consola o que o Gemini respondeu de facto)
            log.info("Resposta bruta do Gemini: {}", textResponse);
            log.info("JSON limpo para conversão: {}", jsonClean);

            // 3. Converter para Objeto Java
            ListJogosResponse result = objectMapper.readValue(jsonClean, ListJogosResponse.class);

            // Se após converter a lista estiver vazia, talvez o modelo não tenha encontrado nada
            if (result.getDias() == null || result.getDias().isEmpty()) {
                log.warn("O modelo não encontrou jogos para o período solicitado.");
            }

            return result;

        } catch (Exception e) {
            log.error("Erro ao converter JSON: {}", e.getMessage());
            throw new RuntimeException("Erro ao processar jogos: " + e.getMessage());
        }
    }

    @Override
    public Optional<ListJogosResponse> loadFromS3jogos(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);

            // Verifica se o plano tem menos de 7 dias (opcional)
            Instant lastModified = s3Object.response().lastModified();
            if (Duration.between(lastModified, Instant.now()).toDays() > 1) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(s3Object, ListJogosResponse.class));
        } catch (Exception e) {
            return Optional.empty(); // Arquivo não existe ou erro na leitura
        }
    }

    @Override
    public void saveToS3(String key, ListJogosResponse plan) {
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
