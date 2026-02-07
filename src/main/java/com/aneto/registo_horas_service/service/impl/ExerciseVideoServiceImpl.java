package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.models.Training.Exercises;
import com.aneto.registo_horas_service.repository.ExerciseRepository;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseVideoServiceImpl implements ExerciseVideoService {

    private final ExerciseRepository repository;


    @Override
    @Cacheable(value = "exerciseVideos", key = "#exerciseName.toLowerCase().trim()", unless = "#result == null")
    public String getVideoUrl(String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) return "";

        log.info("Buscando vídeo para: {}", exerciseName);

        return repository.findByNameIgnoreCase(exerciseName.trim())
                .map(Exercises::getVideoUrl)
                .orElseGet(() -> buildFallbackUrl(exerciseName));
    }

    @Override
    @CacheEvict(value = "exerciseVideos", key = "#exerciseName.toLowerCase().trim()")
    public void evictCache(String exerciseName) {
        log.info("Invalidando cache para: {}", exerciseName);
        // O Spring cuida da remoção no Redis via anotação
    }
    @Override
    public String buildFallbackUrl(String name) {
        return "https://www.youtube.com/results?search_query=" + name.trim().replace(" ", "+");
    }
}