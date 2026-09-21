package com.aneto.registo_horas_service.service;

import com.aneto.registo_horas_service.dto.response.ExerciseCatalogItemDTO;

import java.util.List;
import java.util.Map;

public interface ExerciseVideoService {

    /**
     * Retorna a URL do vídeo (do Cache ou DB).
     */
    String getVideoUrl(String exerciseName);

    Map<String, List<String>> getExerciseDictionary(); // NOVO
    /**
     * Remove a entrada do Redis para garantir sincronização.
     */
    void evictCache(String exerciseName);

    /**
     * Gera uma URL de pesquisa caso o exercício não exista no banco.
     */
    String buildFallbackUrl(String name);

    List<ExerciseCatalogItemDTO> getExerciseCatalog();

    // ExerciseVideoService.java (interface) — novo método
    Map<String, Map<String, List<String>>> getExerciseDictionaryComSubcategoria();
}