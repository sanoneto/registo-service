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

        // NOVO: se já é um URL completo (YouTube, R2 público, CDN, etc.),
        // usa tal como está — não tentes assinar um "objeto" que não existe no bucket.
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }

        // Só chega aqui se for mesmo uma key relativa dentro do bucket R2.
        return generatePresignedUrl(objectKey);
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