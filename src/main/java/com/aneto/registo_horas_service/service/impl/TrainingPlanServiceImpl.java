package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.ExerciseHistoryResponse;
import com.aneto.registo_horas_service.dto.response.PlanoResponseDTO;
import com.aneto.registo_horas_service.dto.response.TrainingExercise;
import com.aneto.registo_horas_service.dto.response.TrainingPlanResponse;
import com.aneto.registo_horas_service.mapper.ExerciseHistoryMapper;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.models.ExerciseHistoryEntity;
import com.aneto.registo_horas_service.models.Training.Training;
import com.aneto.registo_horas_service.repository.ExerciseHistoryRepository;
import com.aneto.registo_horas_service.service.PlanoService;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Convenção de logs:
 *  - DEBUG: diagnóstico detalhado. Desligado por defeito em produção; liga-se/desliga-se
 *           por logger (Actuator /loggers ou variável de ambiente), sem mexer no código.
 *  - INFO : fluxo normal (mantidos os originais).
 *  - WARN / ERROR: falhas reais. Ficam sempre visíveis.
 *
 * Não se logam dados de saúde do aluno (perfil corporal, relatório médico).
 */
@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {
    private static final Logger log = LoggerFactory.getLogger(TrainingPlanServiceImpl.class);

    private static final int DIAS_LIMITE_PARA_REINICIAR_CICLO = 14;

    private final ObjectMapper objectMapper;
    private final PlanoService planoService;
    private final ExerciseHistoryMapper exerciseHistoryMapper;
    private final ExerciseHistoryRepository exerciseHistoryRepository;
    private final Training training;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${spring.cloud.aws.s3.folder-name}")
    private String S3FOLDER;

    @Override
    public TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username, String planId) {
        long inicio = System.currentTimeMillis();
        log.debug("[getOrGeneratePlan] ENTRADA | username='{}' | planId='{}' | request presente? {} | bucket='{}' | folder='{}'",
                username, planId, request != null, bucketName, S3FOLDER);

        String key = buscarChaveDoPlano(planId, username, request);
        log.info("Chave determinada: {}", key);

        if (!isLinkValido(key)) {
            log.warn("[getOrGeneratePlan] Chave nula/vazia (planId='{}', username='{}')", planId, username);
        }

        boolean vazio = isRequestEmpty(request);
        log.debug("[getOrGeneratePlan] isRequestEmpty={}", vazio);

        if (vazio) {
            log.info("Request vazio. A carregar plano existente do S3...");
            Optional<TrainingPlanResponse> loaded = loadFromS3(key);

            if (loaded.isPresent()) {
                TrainingPlanResponse p = loaded.get();
                log.debug("[getOrGeneratePlan] Plano carregado | key='{}' | dias={} | {} ms",
                        key,
                        p.getPlan() != null ? String.valueOf(p.getPlan().size()) : "plan=NULL",
                        System.currentTimeMillis() - inicio);
                return p;
            }

            // WARN (sempre visível): é este o caso que faz o controller responder 200 sem corpo.
            log.warn("[getOrGeneratePlan] Plano NÃO carregado do S3 | key='{}' | planId='{}' | username='{}' -> devolve null",
                    key, planId, username);
            return null;
        }

        log.info("Novo pedido de geração detetado. A verificar histórico para evitar repetição...");

        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();
        log.debug("[getOrGeneratePlan] GERAÇÃO | isAlunoSemConta={} | alunoTempId='{}' | studentUsername='{}'",
                isAlunoSemConta, request.getAlunoTempId(), request.getStudentUsername());

        // 3.1. Busca o plano ativo/concluído anterior — por alunoTempId (aluno sem
        // conta) ou por username (fluxo normal), para evitar misturar históricos
        // de alunos fictícios diferentes.
        Optional<PlanoResponseDTO> planoAnteriorOpt = isAlunoSemConta
                ? planoService.findAtivoAndConcluidoByAlunoTempId(request.getAlunoTempId())
                : planoService.findAtivoAndConcluidoByUsername(username);

        log.debug("[getOrGeneratePlan] Plano anterior ativo/concluído? {} | id={} | link='{}'",
                planoAnteriorOpt.isPresent(),
                planoAnteriorOpt.map(PlanoResponseDTO::getId).orElse(null),
                planoAnteriorOpt.map(PlanoResponseDTO::getLink).orElse(null));

        List<String> exerciciosParaEvitar = new java.util.ArrayList<>();
        planoAnteriorOpt.ifPresent(plano -> {
            Optional<TrainingPlanResponse> antigo = loadFromS3(plano.getLink());
            if (antigo.isEmpty()) {
                log.warn("[getOrGeneratePlan] Plano anterior sem ficheiro legível no S3 | link='{}'", plano.getLink());
            }
            antigo.ifPresent(oldPlan -> {
                if (oldPlan.getPlan() != null) {
                    List<String> names = oldPlan.getPlan().stream()
                            .flatMap(day -> day.getExercises().stream())
                            .map(TrainingExercise::getName)
                            .distinct()
                            .toList();
                    exerciciosParaEvitar.addAll(names);
                }
            });
        });
        log.debug("[getOrGeneratePlan] Exercícios a evitar: {}", exerciciosParaEvitar.size());

        int weekNumber = calcularProximaSemanaCiclo(planoAnteriorOpt, isAlunoSemConta ? request.getAlunoTempId() : username);
        boolean isDeloadWeek = weekNumber % 4 == 0;
        log.info("Identificador {} — semana de ciclo calculada: {} (deload: {})",
                isAlunoSemConta ? request.getAlunoTempId() : username, weekNumber, isDeloadWeek);

        long inicioIa = System.currentTimeMillis();
        TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);
        log.debug("[getOrGeneratePlan] IA respondeu em {} ms | plano {}",
                System.currentTimeMillis() - inicioIa, newPlan == null ? "NULL" : "OK");

        configurarNovoPlano(newPlan, request);
        salvarDadosDoPlano(username, request, key, newPlan, planId, false, weekNumber, isDeloadWeek);
        log.debug("[getOrGeneratePlan] Plano guardado | key='{}' | total {} ms", key, System.currentTimeMillis() - inicio);

        return newPlan;
    }

    @Override
    public void updatePlan(TrainingPlanResponse newPlan, String username, String planId) {
        log.debug("[updatePlan] ENTRADA | username='{}' | planId='{}'", username, planId);

        String key = buscarChaveDoPlano(planId, username, null);
        configurarNovoPlano(newPlan, newPlan.getUserProfile());

        int semanaCiclo = 1;
        boolean isDeload = false;
        if (planId != null && !planId.isBlank()) {
            try {
                PlanoResponseDTO existente = planoService.getByPlanoById(UUID.fromString(planId));
                if (existente != null) {
                    semanaCiclo = existente.getSemanaCiclo();
                    isDeload = existente.isDeload();
                }
                log.debug("[updatePlan] Plano existente? {} | semanaCiclo={} | deload={}",
                        existente != null, semanaCiclo, isDeload);
            } catch (Exception e) {
                log.warn("Não foi possível recuperar semanaCiclo/deload do plano {} — a usar valores default.", planId, e);
            }
        }

        salvarDadosDoPlano(username, newPlan.getUserProfile(), key, newPlan, planId, true, semanaCiclo, isDeload);
        log.debug("[updatePlan] CONCLUÍDO | key='{}'", key);
    }

    // >>> NOVO: associa um plano "sem conta" a uma conta real de aluno
    @Override
    @Transactional
    public void associarPlanoAConta(String planId, String novoUsername) {
        log.debug("[associarPlanoAConta] planId='{}' novoUsername='{}'", planId, novoUsername);

        PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
        if (plano == null) {
            throw new RuntimeException("Plano não encontrado para o ID: " + planId);
        }

        PlanoRequestDTO dto = PlanoRequestDTO.builder()
                .nomeAluno(novoUsername)
                .objetivo(plano.getObjetivo())
                .especialista(plano.getEspecialista())
                .estadoPlano(Enum.EstadoPlano.valueOf(plano.getEstadoPlano()))
                .estadoPedido(Enum.EstadoPedido.valueOf(plano.getEstadoPedido()))
                .link(plano.getLink())
                .recommended(plano.getRecommended())
                .semanaCiclo(plano.getSemanaCiclo())
                .deload(plano.isDeload())
                .contaAssociada(true)
                .alunoTempId(null)
                .build();

        planoService.updatePlano(planId, dto);
        log.info("Plano {} associado com sucesso à conta '{}'.", planId, novoUsername);
    }

    @Override
    @Transactional
    public void saveProgressLogs(List<TrainingExercise> logs, String username, String planId) {
        if (logs == null || logs.isEmpty()) {
            log.debug("[saveProgressLogs] Lista vazia | username='{}' planId='{}'", username, planId);
            return;
        }
        log.debug("[saveProgressLogs] {} registos | username='{}' planId='{}'", logs.size(), username, planId);

        // (renomeado de 'log' para 'item' para não sombrear o Logger da classe)
        List<ExerciseHistoryEntity> entities = logs.stream().map(item -> {
            return ExerciseHistoryEntity.builder()
                    .username(username)
                    .planId(planId)
                    .exerciseName(item.getName())
                    .muscleGroup(item.getMuscleGroup())
                    .weight(item.getWeight())
                    .registeredAt(LocalDateTime.now())
                    .clientDate(item.getDate())
                    .build();
        }).collect(Collectors.toList());

        exerciseHistoryRepository.saveAll(entities);
    }

    @Override
    public List<ExerciseHistoryResponse> getProgressLogs(String exerciseName, String username) {
        var entities = exerciseHistoryRepository.findByUsernameAndExerciseNameOrderByRegisteredAtDesc(username, exerciseName);
        log.debug("[getProgressLogs] exercise='{}' username='{}' -> {} registos", exerciseName, username, entities.size());
        return exerciseHistoryMapper.toResponseList(entities);
    }

    @Override
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
            // ERROR com stack trace, sempre visível: credenciais, região, endpoint, JSON inválido, key nula...
            log.error("[loadFromS3] ERRO a ler | bucket='{}' key='{}'", bucketName, key, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveToS3(String key, TrainingPlanResponse plan) {
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

    private int calcularProximaSemanaCiclo(Optional<PlanoResponseDTO> planoAnteriorOpt, String identificador) {
        if (planoAnteriorOpt.isEmpty()) {
            log.debug("[calcularSemanaCiclo] {} sem plano anterior -> semana 1", identificador);
            return 1;
        }

        PlanoResponseDTO anterior = planoAnteriorOpt.get();

        String dataReferenciaStr = (anterior.getDataUpdate() != null && !anterior.getDataUpdate().isBlank())
                ? anterior.getDataUpdate() : anterior.getDataCreate();

        if (dataReferenciaStr == null || dataReferenciaStr.isBlank()) {
            log.warn("Plano anterior de {} sem data de referência válida — a reiniciar ciclo.", identificador);
            return 1;
        }

        try {
            LocalDate dataReferencia = LocalDate.parse(dataReferenciaStr);
            long diasDesdeUltimoPlano = ChronoUnit.DAYS.between(dataReferencia, LocalDate.now());
            log.debug("[calcularSemanaCiclo] {} | dataRef={} | dias desde último={} | semanaAnterior={}",
                    identificador, dataReferencia, diasDesdeUltimoPlano, anterior.getSemanaCiclo());

            if (diasDesdeUltimoPlano > DIAS_LIMITE_PARA_REINICIAR_CICLO) {
                log.info("{} esteve {} dias sem gerar plano novo — a reiniciar ciclo de periodização.",
                        identificador, diasDesdeUltimoPlano);
                return 1;
            }

            return anterior.getSemanaCiclo() + 1;
        } catch (Exception e) {
            log.warn("Não foi possível interpretar a data de referência ('{}') do plano anterior de {} — a reiniciar ciclo.",
                    dataReferenciaStr, identificador);
            return 1;
        }
    }

    private String buscarChaveDoPlano(String planId, String username, UserProfileRequest request) {
        log.info("dentro de buscarChaveDoPlano");
        log.debug("[buscarChave] planId='{}' username='{}' request presente? {}", planId, username, request != null);

        if (planId != null && !planId.isBlank()) {
            try {
                PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
                if (plano != null && isLinkValido(plano.getLink())) {
                    log.debug("[buscarChave] link vindo da BD: '{}'", plano.getLink());
                    return plano.getLink();
                }
                log.warn("[buscarChave] Plano {} sem link válido na BD (plano encontrado? {})", planId, plano != null);
            } catch (IllegalArgumentException e) {
                log.error("ID do plano inválido: {}", planId);
            }
        }

        log.info(" Aprocura plano ativo e concluído na base para o utilizador: {}", username);
        if (request == null) {
            return planoService.findAtivoAndConcluidoByUsername(username)
                    .map(PlanoResponseDTO::getLink)
                    .filter(this::isLinkValido)
                    .orElseGet(() -> {
                        log.info("Nenhum plano ativo encontrado para {}. Gerando fallback.", username);
                        return gerarCaminhoPadrao(username);
                    });
        } else {
            return gerarCaminhoPadrao(username);
        }
    }

    private boolean isLinkValido(String link) {
        return link != null && !link.isBlank();
    }

    private String gerarCaminhoPadrao(String username) {
        log.info("dentro de gerarCaminhoPadrao");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String key = String.format("%s%s/plan/%s_%s.json", S3FOLDER, username, username, timestamp);
        log.info("A key na função gerarCaminhoPadrao {}", key);
        return key;
    }

    private boolean isRequestEmpty(UserProfileRequest request) {
        boolean vazio = request == null ||
                (request.getObjective() == null || request.getObjective().isBlank()) ||
                request.getWeightKg() == null ||
                request.getHeightCm() == null ||
                request.getAge() == null;

        // Só indica QUAIS campos faltam (sem valores), para perceber porque é tratado como "vazio".
        if (log.isDebugEnabled() && request != null) {
            log.debug("[isRequestEmpty] vazio={} | objective? {} | weightKg? {} | heightCm? {} | age? {}",
                    vazio,
                    request.getObjective() != null && !request.getObjective().isBlank(),
                    request.getWeightKg() != null,
                    request.getHeightCm() != null,
                    request.getAge() != null);
        }
        return vazio;
    }

    private void configurarNovoPlano(TrainingPlanResponse plan, UserProfileRequest request) {
        plan.setIsExistingPlan(true);
        plan.setUserProfile(request);
    }

    private void salvarDadosDoPlano(String username, UserProfileRequest request, String key, TrainingPlanResponse plan,
                                    String planId, boolean update, int semanaCiclo, boolean isDeload) {
        PlanoRequestDTO dto;

        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();
        log.debug("[salvarDadosDoPlano] username='{}' | planId='{}' | update={} | isAlunoSemConta={} | semanaCiclo={} | deload={} | key='{}'",
                username, planId, update, isAlunoSemConta, semanaCiclo, isDeload, key);

        if (update) {
            if (isAlunoSemConta) {
                planoService.prepararNovoPlanoAtivoPorAlunoTempId(request.getAlunoTempId());
            } else {
                planoService.prepararNovoPlanoAtivo(username);
            }
        }

        if (planId == null || planId.isEmpty()) {
            boolean temNomeAluno = request.getStudentName() != null && !request.getStudentName().isBlank();

            String nomeNoPlano = temNomeAluno ? request.getStudentName() : username;
            String especialista = temNomeAluno ? username : "Sem Especialista";
            String recommended = temNomeAluno ? request.getRecommended() : username;

            dto = PlanoRequestDTO.builder()
                    .nomeAluno(nomeNoPlano)
                    .objetivo(request.getObjective())
                    .especialista(especialista)
                    .estadoPlano(Enum.EstadoPlano.ATIVO)
                    .estadoPedido(Enum.EstadoPedido.PENDENTE)
                    .link(key)
                    .recommended(recommended)
                    .semanaCiclo(semanaCiclo)
                    .deload(isDeload)
                    .contaAssociada(!isAlunoSemConta)
                    .alunoTempId(isAlunoSemConta ? request.getAlunoTempId() : null)
                    .build();
            log.debug("[salvarDadosDoPlano] CRIAR plano | nomeAluno='{}' | especialista='{}'", nomeNoPlano, especialista);
            planoService.createPlano(dto);
        } else {
            PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));

            if (planoExistente == null) {
                log.error("[salvarDadosDoPlano] Plano não encontrado para o ID: {}", planId);
                throw new RuntimeException("Plano não encontrado para o ID: " + planId);
            }

            boolean temNomeAluno = request.getStudentName() != null && !request.getStudentName().isBlank();
            String especialistaAtualizado = temNomeAluno ? username : "Sem Especialista";

            dto = PlanoRequestDTO.builder()
                    .nomeAluno(planoExistente.getNomeAluno())
                    .objetivo(planoExistente.getObjetivo())
                    .especialista(especialistaAtualizado)
                    .estadoPlano(Enum.EstadoPlano.ATIVO)
                    .estadoPedido(Enum.EstadoPedido.FINALIZADO)
                    .link(key)
                    .recommended(planoExistente.getRecommended())
                    .semanaCiclo(semanaCiclo)
                    .deload(isDeload)
                    .contaAssociada(planoExistente.isContaAssociada())
                    .alunoTempId(planoExistente.getAlunoTempId())
                    .build();
            log.debug("[salvarDadosDoPlano] ATUALIZAR plano {} | especialista='{}'", planId, especialistaAtualizado);
            planoService.updatePlano(planId, dto);
        }

        saveToS3(key, plan);
    }
}