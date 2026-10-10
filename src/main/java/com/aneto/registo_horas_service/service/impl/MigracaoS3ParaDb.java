package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.models.Plano;
import com.aneto.registo_horas_service.repository.PlanoRepository; // ajusta ao nome real do teu repositório
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Corre UMA vez, no arranque, só se migracao.s3.ativa=true.
 * Lê cada JSON do S3 (PlanoS3Storage antigo) e grava-o em registos.plano_dados.
 * Não apaga nada no S3. É seguro repetir: o que já existe é ignorado.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "migracao.s3.ativa", havingValue = "true")
public class MigracaoS3ParaDb implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracaoS3ParaDb.class);

    private final PlanoRepository planoRepository;
    private final PlanoS3Storage s3Storage;
    private final PlanoDbStorage dbStorage;

    @Override
    public void run(String... args) {
        List<String> links = planoRepository.findAll().stream()
                .map(Plano::getLink)
                .filter(l -> l != null && !l.isBlank())
                .distinct()
                .toList();

        int migrados = 0, jaExistiam = 0, semFicheiro = 0, erros = 0;
        log.info("### Migração S3 -> BD: {} links distintos", links.size());

        for (String link : links) {
            try {
                if (dbStorage.existe(link)) {
                    jaExistiam++;
                    continue;
                }
                var plano = s3Storage.loadFromS3(link);
                if (plano.isPresent()) {
                    dbStorage.save(link, plano.get());
                    migrados++;
                } else {
                    semFicheiro++;
                    log.warn("Sem ficheiro no S3 para o link '{}'", link);
                }
            } catch (Exception e) {
                erros++;
                log.error("Erro ao migrar '{}': {}", link, e.getMessage());
            }
        }
        log.info("### Migração concluída: migrados={}, jaExistiam={}, semFicheiro={}, erros={}",
                migrados, jaExistiam, semFicheiro, erros);
    }
}