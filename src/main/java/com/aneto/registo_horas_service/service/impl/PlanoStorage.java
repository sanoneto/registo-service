package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ponto único de acesso ao JSON do plano durante a transição S3 -> BD.
 *
 * LEITURA: base de dados primeiro; o S3 só é consultado se a BD não tiver o plano
 *          (ou falhar). Quando o S3 responde, o plano é copiado para a BD, por isso
 *          cada plano só vai ao S3 uma vez.
 * ESCRITA: grava sempre na BD (fonte de verdade). A cópia para o S3 é opcional e
 *          nunca faz o pedido falhar: se o S3 estiver em baixo, só regista o erro.
 *
 * Depende só de PlanoS3Storage e PlanoDbStorage, por isso não cria ciclos de dependência.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanoStorage {

    private final PlanoS3Storage s3Storage;
    private final PlanoDbStorage dbStorage;

    // Pôr plano.storage.escrever-s3=false quando já não quiseres cópias no S3 (poupa pedidos).
    @Value("${plano.storage.escrever-s3:true}")
    private boolean escreverS3;

    public Optional<TrainingPlanResponse> load(String key) {
        // 1. Base de dados primeiro
        try {
            Optional<TrainingPlanResponse> daBd = dbStorage.load(key);
            if (daBd.isPresent()) {
                return daBd;
            }
            log.debug("[PlanoStorage] Plano não está na BD (key='{}'). A tentar o S3.", key);
        } catch (Exception e) {
            log.warn("[PlanoStorage] Erro ao ler da BD (key='{}'): {}. A tentar o S3.", key, e.getMessage());
        }

        // 2. Fallback: S3 (planos ainda não migrados)
        try {
            Optional<TrainingPlanResponse> doS3 = s3Storage.loadFromS3(key);
            if (doS3.isPresent()) {
                copiarParaDb(key, doS3.get());
                return doS3;
            }
        } catch (Exception e) {
            log.warn("[PlanoStorage] Erro ao ler do S3 (key='{}'): {}", key, e.getMessage());
        }

        log.warn("[PlanoStorage] Plano não encontrado na BD nem no S3 (key='{}').", key);
        return Optional.empty();
    }

    public void save(String key, TrainingPlanResponse plan) {
        // BD primeiro: fonte de verdade. Se falhar, o erro propaga-se (o plano não ficou guardado).
        dbStorage.save(key, plan);

        // S3: cópia de segurança, nunca bloqueia o pedido.
        if (!escreverS3) {
            return;
        }
        try {
            s3Storage.saveToS3(key, plan);
        } catch (Exception e) {
            log.error("[PlanoStorage] Plano gravado na BD, mas a cópia para o S3 falhou (key='{}'): {}",
                    key, e.getMessage());
        }
    }

    private void copiarParaDb(String key, TrainingPlanResponse plan) {
        try {
            dbStorage.save(key, plan);
            log.info("[PlanoStorage] Plano copiado do S3 para a BD (key='{}').", key);
        } catch (Exception e) {
            log.warn("[PlanoStorage] Não foi possível copiar para a BD (key='{}'): {}", key, e.getMessage());
        }
    }
}