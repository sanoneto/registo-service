package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.ListJogosResponse;
import com.aneto.registo_horas_service.models.JogoTV;
import com.aneto.registo_horas_service.repository.JogoTVRepository;
import com.aneto.registo_horas_service.repository.PlanoRepository;
import com.aneto.registo_horas_service.service.DashboardService;
import com.aneto.registo_horas_service.service.FootballService;
import com.aneto.registo_horas_service.service.ProgramacaoTVService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private final ObjectMapper objectMapper;
    //private final JogoTVRepository jogoTVRepository;
    private final FootballService footballService;
    //private final ProgramacaoTVService programacaoTVService;

    private final S3Client s3Client;
    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${spring.cloud.aws.s3.folder-name}")
    private String S3FOLDER;

    @Value("${FootballData.dataInicio}")
    private String dataInicio;

    @Value("${FootballData.dataFim}")
    private String dataFim;

    @Override
    public ListJogosResponse getListJogo(String username) {
        String key = S3FOLDER + username + "/jogos/" + username + ".json";
        // Tentar buscar plano existente se não foram enviados dados novos
        Optional<ListJogosResponse> existingPlan = loadFromS3jogos(key);

      // List<JogoTV> jogos= programacaoTVService.extrairJogos();
        //List<JogoTV> jogos = jogoTVRepository.findAll();

// Isto imprime linha a linha na consola do IntelliJ/Eclipse
       /* jogos.forEach(jogo -> {
            System.out.println("-------------------------");
            System.out.println("Equipas: " + jogo.getEquipas());
            System.out.println("Canal:   " + jogo.getCanal());
            System.out.println("Hora:    " + jogo.getHora());
        });*/

        if (existingPlan.isPresent()) {
            return existingPlan.get();
        }
        // 2. Se não existir ou for antigo, gera novo
        ListJogosResponse  newPlan = footballService.buscarJogosParaReact(dataInicio,dataFim).block();
        //guarda os input e true é para depois o escrever que o plano ja existe
        saveToS3(key, newPlan);

        return newPlan;
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
