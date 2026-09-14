package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.models.Training.Exercises;
import com.aneto.registo_horas_service.repository.ExerciseRepository;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExerciseVideoServiceImpl implements ExerciseVideoService {

    private final ExerciseRepository repository;
    private final S3Presigner r2Presigner;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.url-expiration-minutes:30}")
    private long urlExpirationMinutes;

    // NOVO: base-url do ambiente atual (dev -> r2.dev, prod -> cdn.liveproact.com)
    @Value("${r2.video-base-url}")
    private String videoBaseUrl;

    // NOVO: prefixo do caminho dentro do bucket/domínio (ex: "exercicios")
    @Value("${r2.video-path-prefix:exercicios}")
    private String videoPathPrefix;

    public ExerciseVideoServiceImpl(ExerciseRepository repository,
                                    @Qualifier("r2Presigner") S3Presigner r2Presigner) {
        this.repository = repository;
        this.r2Presigner = r2Presigner;
    }

    @Override
    @Cacheable(value = "exerciseVideos", key = "#exerciseName.toLowerCase().trim()", unless = "#result == null")
    public String getVideoUrl(String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) return "";

        log.info("Buscando vídeo para: {}", exerciseName);

        String objectKey = repository.findByNameIgnoreCase(exerciseName.trim())
                .map(Exercises::getVideoUrl)
                .orElse(null);

        if (objectKey == null || objectKey.isBlank()) {
            return buildFallbackUrl(exerciseName);
        }

        // Se já é um URL completo (YouTube, ou alguém ainda gravou link absoluto na BD),
        // usa tal como está — mantém compatibilidade com dados antigos.
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }

        // NOVO FLUXO: a BD só guarda o "número"/nome do ficheiro (ex: "Y-W-T.mp4").
        // Montamos o URL público completo consoante o ambiente ativo.
        return buildPublicUrl(objectKey);
    }

    /**
     * Constrói o URL público completo a partir do base-url do ambiente
     * (definido em application-{profile}.yml) e do caminho relativo guardado
     * na BD. Ex: dev -> https://pub-....r2.dev/exercicios/Y-W-T.mp4
     *            prod -> https://cdn.liveproact.com/exercicios/Y-W-T.mp4
     */
    private String buildPublicUrl(String objectKey) {
        String base = videoBaseUrl.endsWith("/")
                ? videoBaseUrl.substring(0, videoBaseUrl.length() - 1)
                : videoBaseUrl;

        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;

        // Se o valor na BD já vier com o prefixo "exercicios/", não duplica.
        boolean jaTemPrefixo = videoPathPrefix != null && !videoPathPrefix.isBlank()
                && key.toLowerCase().startsWith(videoPathPrefix.toLowerCase() + "/");

        String path = jaTemPrefixo || videoPathPrefix == null || videoPathPrefix.isBlank()
                ? key
                : videoPathPrefix + "/" + key;

        return base + "/" + path;
    }

    @Override
    @Cacheable(value = "exerciseDictionary")
    public Map<String, List<String>> getExerciseDictionary() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Exercises::getCategory,
                        Collectors.mapping(Exercises::getName, Collectors.toList())
                ));
    }

    // Mantido apenas para casos em que precises mesmo de acesso privado/assinado
    // a um objeto do bucket (ex: bucket sem domínio público, conteúdo restrito).
    // Já não é chamado no fluxo normal, mas fica disponível se precisares.
    private String generatePresignedUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(urlExpirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        return r2Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    @CacheEvict(value = "exerciseVideos", key = "#exerciseName.toLowerCase().trim()")
    public void evictCache(String exerciseName) {
        log.info("Invalidando cache para: {}", exerciseName);
    }

    @Override
    public String buildFallbackUrl(String name) {
        return "https://www.youtube.com/results?search_query=" + name.trim().replace(" ", "+");
    }

}