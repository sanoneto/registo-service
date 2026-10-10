package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.Optional;

/**
 * NOVO: lógica de leitura/escrita do plano no S3, extraída de TrainingPlanServiceImpl.
 *
 * Motivo da extração: o novo TrainingGenerationAsyncService (que corre a geração em
 * background) também precisa de gravar/ler planos no S3. Se dependesse diretamente de
 * TrainingPlanService (a interface implementada por TrainingPlanServiceImpl), criava-se
 * uma dependência circular, porque TrainingPlanServiceImpl passa a depender de
 * TrainingGenerationAsyncService para disparar o trabalho assíncrono:
 *
 *   TrainingPlanServiceImpl -> TrainingGenerationAsyncService -> TrainingPlanService (interface)
 *                                                                        ^
 *                                                        implementada por TrainingPlanServiceImpl
 *
 * Com este componente isolado, ambos (TrainingPlanServiceImpl e
 * TrainingGenerationAsyncService) dependem apenas de PlanoS3Storage, sem ciclo.
 * TrainingPlanServiceImpl.loadFromS3/saveToS3 passam a ser um simples delegate para aqui,
 * preservando o contrato público da interface TrainingPlanService sem alterações para
 * quem já a consome.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanoS3Storage {

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final PlanoDbStorage planoDbStorage;

    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;

    public Optional<TrainingPlanResponse> loadFromS3(String key) {
        log.debug("[loadFromS3] bucket='{}' key='{}'", bucketName, key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            Instant lastModified = s3Object.response().lastModified();
            log.debug("[loadFromS3] objeto encontrado | lastModified={} | size={} bytes",
                    lastModified, s3Object.response().contentLength());

            TrainingPlanResponse plan = objectMapper.readValue(s3Object, TrainingPlanResponse.class);
            log.debug("[loadFromS3] JSON convertido com sucesso | key='{}'", key);
            return Optional.of(plan);
        } catch (NoSuchKeyException e) {
            log.warn("[loadFromS3] Ficheiro NÃO existe | bucket='{}' key='{}'", bucketName, key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[loadFromS3] ERRO a ler | bucket='{}' key='{}'", bucketName, key, e);
            return Optional.empty();
        }
    }

    public void saveToS3(String key, TrainingPlanResponse plan) {
        log.info("salvar no base de dados :");
        planoDbStorage.save(key, plan);

        log.debug("[saveToS3] bucket='{}' key='{}'", bucketName, key);
        try {
            String json = objectMapper.writeValueAsString(plan);
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    RequestBody.fromString(json));
            log.debug("[saveToS3] guardado | key='{}' | {} chars", key, json.length());
        } catch (Exception e) {
            log.error("Erro ao salvar no S3 | bucket='{}' key='{}'", bucketName, key, e);
            throw new RuntimeException("Falha ao salvar o plano de treino no S3: " + e.getMessage(), e);
        }
    }
}