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
        String key = buscarChaveDoPlano(planId, username, request);
        log.info("Chave determinada: {}", key);

        if (isRequestEmpty(request)) {
            log.info("Request vazio. A carregar plano existente do S3...");
            return loadFromS3(key).orElse(null);
        }

        log.info("Novo pedido de geração detetado. A verificar histórico para evitar repetição...");

        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();

        // 3.1. Busca o plano ativo/concluído anterior — por alunoTempId (aluno sem
        // conta) ou por username (fluxo normal), para evitar misturar históricos
        // de alunos fictícios diferentes.
        Optional<PlanoResponseDTO> planoAnteriorOpt = isAlunoSemConta
                ? planoService.findAtivoAndConcluidoByAlunoTempId(request.getAlunoTempId())
                : planoService.findAtivoAndConcluidoByUsername(username);

        List<String> exerciciosParaEvitar = new java.util.ArrayList<>();
        planoAnteriorOpt.ifPresent(plano -> {
            loadFromS3(plano.getLink()).ifPresent(oldPlan -> {
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

        int weekNumber = calcularProximaSemanaCiclo(planoAnteriorOpt, isAlunoSemConta ? request.getAlunoTempId() : username);
        boolean isDeloadWeek = weekNumber % 4 == 0;
        log.info("Identificador {} — semana de ciclo calculada: {} (deload: {})",
                isAlunoSemConta ? request.getAlunoTempId() : username, weekNumber, isDeloadWeek);

        TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);

        configurarNovoPlano(newPlan, request);
        salvarDadosDoPlano(username, request, key, newPlan, planId, false, weekNumber, isDeloadWeek);

        return newPlan;
    }

    @Override
    public void updatePlan(TrainingPlanResponse newPlan, String username, String planId) {
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
            } catch (Exception e) {
                log.warn("Não foi possível recuperar semanaCiclo/deload do plano {} — a usar valores default.", planId);
            }
        }

        salvarDadosDoPlano(username, newPlan.getUserProfile(), key, newPlan, planId, true, semanaCiclo, isDeload);
    }

    // >>> NOVO: associa um plano "sem conta" a uma conta real de aluno
    @Override
    @Transactional
    public void associarPlanoAConta(String planId, String novoUsername) {
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
        if (logs == null || logs.isEmpty()) return;

        List<ExerciseHistoryEntity> entities = logs.stream().map(log -> {
            return ExerciseHistoryEntity.builder()
                    .username(username)
                    .planId(planId)
                    .exerciseName(log.getName())
                    .muscleGroup(log.getMuscleGroup())
                    .weight(log.getWeight())
                    .registeredAt(LocalDateTime.now())
                    .clientDate(log.getDate())
                    .build();
        }).collect(Collectors.toList());

        exerciseHistoryRepository.saveAll(entities);
    }

    @Override
    public List<ExerciseHistoryResponse> getProgressLogs(String exerciseName, String username) {
        var entities = exerciseHistoryRepository.findByUsernameAndExerciseNameOrderByRegisteredAtDesc(username, exerciseName);
        return exerciseHistoryMapper.toResponseList(entities);
    }

    @Override
    public Optional<TrainingPlanResponse> loadFromS3(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            Instant lastModified = s3Object.response().lastModified();
            return Optional.of(objectMapper.readValue(s3Object, TrainingPlanResponse.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void saveToS3(String key, TrainingPlanResponse plan) {
        try {
            String json = objectMapper.writeValueAsString(plan);
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    RequestBody.fromString(json));
        } catch (Exception e) {
            log.error("Erro ao salvar no S3", e);
        }
    }

    private int calcularProximaSemanaCiclo(Optional<PlanoResponseDTO> planoAnteriorOpt, String identificador) {
        if (planoAnteriorOpt.isEmpty()) {
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
        if (planId != null && !planId.isBlank()) {
            try {
                PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
                if (plano != null && isLinkValido(plano.getLink())) {
                    return plano.getLink();
                }
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
        return request == null ||
                (request.getObjective() == null || request.getObjective().isBlank()) ||
                request.getWeightKg() == null ||
                request.getHeightCm() == null ||
                request.getAge() == null;
    }

    private void configurarNovoPlano(TrainingPlanResponse plan, UserProfileRequest request) {
        plan.setIsExistingPlan(true);
        plan.setUserProfile(request);
    }

    private void salvarDadosDoPlano(String username, UserProfileRequest request, String key, TrainingPlanResponse plan,
                                    String planId, boolean update, int semanaCiclo, boolean isDeload) {
        PlanoRequestDTO dto;

        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();

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
            planoService.createPlano(dto);
        } else {
            PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));

            if (planoExistente == null) {
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
            planoService.updatePlano(planId, dto);
        }

        saveToS3(key, plan);
    }
}