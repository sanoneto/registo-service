package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.ExerciseCatalogItemDTO;
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
import java.util.Comparator;
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

    @Value("${r2.video-base-url}")
    private String videoBaseUrl;

    @Value("${r2.video-path-prefix:exercicios}")
    private String videoPathPrefix;

    public ExerciseVideoServiceImpl(ExerciseRepository repository,
                                    @Qualifier("r2Presigner") S3Presigner r2Presigner) {
        this.repository = repository;
        this.r2Presigner = r2Presigner;
    }

    /**
     * ÚNICA fonte de verdade: carrega a tabela inteira UMA vez (cache),
     * indexada por nome normalizado (upper+trim), para servir tanto
     * o dicionário do prompt como a resolução de vídeos.
     * Se a tabela crescer muito no futuro, isto ainda é barato —
     * é uma tabela de referência, não transacional.
     */
    @Cacheable(value = "allExercises")
    public Map<String, Exercises> loadExerciseMap() {
        log.info("Carregando dicionário de exercícios da BD (cache miss)...");
        return repository.findAll().stream()
                .collect(Collectors.toMap(
                        e -> e.getName().trim().toUpperCase(),
                        e -> e,
                        (existing, duplicate) -> existing // guarda o primeiro em caso de nomes duplicados
                ));
    }

    @Override
    public String getVideoUrl(String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) return "";

        String normalizedKey = exerciseName.trim().toUpperCase();
        Exercises exercise = loadExerciseMap().get(normalizedKey);

        if (exercise == null || exercise.getVideoUrl() == null || exercise.getVideoUrl().isBlank()) {
            return ""; // sem vídeo real associado — deixa o frontend tratar como "SEM VÍDEO"
        }

        String objectKey = exercise.getVideoUrl();

        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }

        return buildPublicUrl(objectKey);
    }

    private String buildPublicUrl(String objectKey) {
        String base = videoBaseUrl.endsWith("/")
                ? videoBaseUrl.substring(0, videoBaseUrl.length() - 1)
                : videoBaseUrl;

        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;

        boolean jaTemPrefixo = videoPathPrefix != null && !videoPathPrefix.isBlank()
                && key.toLowerCase().startsWith(videoPathPrefix.toLowerCase() + "/");

        String path = jaTemPrefixo || videoPathPrefix == null || videoPathPrefix.isBlank()
                ? key
                : videoPathPrefix + "/" + key;

        return base + "/" + path;
    }

    @Override
    public Map<String, List<String>> getExerciseDictionary() {
        return loadExerciseMap().values().stream()
                .collect(Collectors.groupingBy(
                        Exercises::getCategory,
                        Collectors.mapping(Exercises::getName, Collectors.toList())
                ));
    }

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

    /**
     * Invalida o cache inteiro — como agora só há UMA fonte de dados,
     * já não faz sentido invalidar por nome individual: se um exercício
     * mudou, o mapa todo tem de ser recarregado.
     */
    @Override
    @CacheEvict(value = "allExercises", allEntries = true)
    public void evictCache(String exerciseName) {
        log.info("Cache de exercícios invalidado (alteração em: {})", exerciseName);
    }

    @Override
    public String buildFallbackUrl(String name) {
        return "https://www.youtube.com/results?search_query=" + name.trim().replace(" ", "+");
    }

    @Override
    public List<ExerciseCatalogItemDTO> getExerciseCatalog() {
        return loadExerciseMap().values().stream()
                .map(e -> new ExerciseCatalogItemDTO(
                        e.getName(),
                        e.getCategory(),
                        getVideoUrl(e.getName()),
                        e.getSubcategory()// reaproveita a lógica de resolução de URL/fallback
                ))
                .sorted(Comparator.comparing(ExerciseCatalogItemDTO::category)
                        .thenComparing(ExerciseCatalogItemDTO::name))
                .toList();
    }

    // ExerciseVideoServiceImpl.java
    @Override
    public Map<String, Map<String, List<String>>> getExerciseDictionaryComSubcategoria() {
        return loadExerciseMap().values().stream()
                .collect(Collectors.groupingBy(
                        Exercises::getCategory,
                        Collectors.groupingBy(
                                e -> (e.getSubcategory() == null || e.getSubcategory().isBlank())
                                        ? "GERAL" : e.getSubcategory(),
                                Collectors.mapping(Exercises::getName, Collectors.toList())
                        )
                ));
    }
}