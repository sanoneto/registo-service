package com.aneto.registo_horas_service.service;

public interface ExerciseVideoService {

    /**
     * Retorna a URL do vídeo (do Cache ou DB).
     */
    String getVideoUrl(String exerciseName);

    /**
     * Remove a entrada do Redis para garantir sincronização.
     */
    void evictCache(String exerciseName);

    /**
     * Gera uma URL de pesquisa caso o exercício não exista no banco.
     */
    String buildFallbackUrl(String name);
}