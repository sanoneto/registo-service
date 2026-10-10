package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.models.PlanoDados;
import com.aneto.registo_horas_service.repository.PlanoDadosRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Substitui o PlanoS3Storage: a "key" continua a ser o link do plano,
 * mas o JSON passa a viver na tabela registos.plano_dados.
 */
@Component
@RequiredArgsConstructor
public class PlanoDbStorage {

    private static final Logger log = LoggerFactory.getLogger(PlanoDbStorage.class);

    private final PlanoDadosRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<TrainingPlanResponse> load(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(key).map(row -> {
            try {
                return objectMapper.treeToValue(row.getDados(), TrainingPlanResponse.class);
            } catch (JsonProcessingException e) {
                log.error("JSON do plano inválido para a chave '{}': {}", key, e.getMessage());
                return null; // Optional.map transforma null em vazio
            }
        });
    }

    /** Equivalente a sobrescrever o ficheiro no S3: cria ou substitui. */
    @Transactional
    public void save(String key, TrainingPlanResponse plan) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Chave do plano vazia.");
        }
        JsonNode node = objectMapper.valueToTree(plan);
        repository.save(new PlanoDados(key, node, null));
    }

    @Transactional(readOnly = true)
    public boolean existe(String key) {
        return key != null && repository.existsById(key);
    }
}