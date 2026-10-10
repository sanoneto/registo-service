package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    // Leitura: S3 primeiro, BD como fallback. Escrita: BD + S3 (ver PlanoStorage)
    private final PlanoStorage planoStorage;
    private final PlanoRegistoHelper planoRegistoHelper;
    private final TrainingGenerationAsyncService trainingGenerationAsyncService;

    // Só serve para formar o prefixo do "link" do plano. Com valor por omissão,
    // a configuração do S3 pode ser removida sem partir o arranque.
    @Value("${spring.cloud.aws.s3.folder-name:planos/}")
    private String S3FOLDER;

    @Override
    public TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username, String planId) {
        long inicio = System.currentTimeMillis();
        String key = buscarChaveDoPlano(planId, username, request);

        // 1. Leitura direta (S3 com fallback para a BD; modo consulta / sem body no request)
        if (isRequestEmpty(request)) {
            Optional<TrainingPlanResponse> loaded = loadFromS3(key);
            if (loaded.isPresent()) {
                TrainingPlanResponse planLoaded = loaded.get();
                sincronizarBlocosComPlanoClassico(planLoaded);
                return planLoaded;
            }
            log.warn("[getOrGeneratePlan] Plano não encontrado (S3 nem BD) para a chave '{}'.", key);
            return null;
        }

        // 2. Geração síncrona com IA
        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();
        Optional<PlanoResponseDTO> planoAnteriorOpt = isAlunoSemConta
                ? planoService.findAtivoAndConcluidoByAlunoTempId(request.getAlunoTempId())
                : planoService.findAtivoAndConcluidoByUsername(username);

        List<String> exerciciosParaEvitar = carregarExerciciosParaEvitar(planoAnteriorOpt);
        int weekNumber = calcularProximaSemanaCiclo(planoAnteriorOpt, isAlunoSemConta ? request.getAlunoTempId() : username);
        boolean isDeloadWeek = weekNumber % 4 == 0;

        TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);

        // Mapeia os dados do clinicalPlan/blocks para a lista legada 'plan'
        sincronizarBlocosComPlanoClassico(newPlan);

        configurarNovoPlano(newPlan, request);
        salvarDadosDoPlano(username, request, key, newPlan, planId, false, weekNumber, isDeloadWeek);

        log.info("[getOrGeneratePlan] Plano gerado e guardado em {} ms", System.currentTimeMillis() - inicio);
        return newPlan;
    }

    @Override
    public boolean isPedidoDeGeracaoCompleto(UserProfileRequest request) {
        return !isRequestEmpty(request);
    }

    @Override
    public PlanoResponseDTO iniciarGeracaoAssincrona(UserProfileRequest request, String username, String planId) {
        if (isRequestEmpty(request)) {
            throw new IllegalStateException("Pedido vazio. Utilize a leitura direta do plano.");
        }

        String key = buscarChaveDoPlano(planId, username, request);
        boolean isAlunoSemConta = request.getAlunoTempId() != null && !request.getAlunoTempId().isBlank();

        Optional<PlanoResponseDTO> planoAnteriorOpt = isAlunoSemConta
                ? planoService.findAtivoAndConcluidoByAlunoTempId(request.getAlunoTempId())
                : planoService.findAtivoAndConcluidoByUsername(username);

        List<String> exerciciosParaEvitar = carregarExerciciosParaEvitar(planoAnteriorOpt);
        int weekNumber = calcularProximaSemanaCiclo(planoAnteriorOpt, isAlunoSemConta ? request.getAlunoTempId() : username);
        boolean isDeloadWeek = weekNumber % 4 == 0;

        PlanoResponseDTO planoRegistado = planoRegistoHelper.upsert(username, request, key, planId,
                weekNumber, isDeloadWeek, Enum.EstadoPedido.A_PROCESSAR, null);

        trainingGenerationAsyncService.gerarEGuardarPlano(
                request, username, planoRegistado.getId(), key, weekNumber, isDeloadWeek, exerciciosParaEvitar
        );

        return planoRegistado;
    }

    @Override
    public PlanoStatusResponse getStatusDoPlano(String planId) {
        PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
        if (plano == null) return null;

        PlanoStatusResponse.PlanoStatusResponseBuilder builder = PlanoStatusResponse.builder()
                .planId(plano.getId())
                .estadoPedido(plano.getEstadoPedido())
                .erroMensagem(plano.getErroMensagem());

        String estado = plano.getEstadoPedido();
        // Permite carregar o plano se estiver FINALIZADO ou PENDENTE
        if (Enum.EstadoPedido.FINALIZADO.getDescricao().equalsIgnoreCase(estado) ||
                Enum.EstadoPedido.PENDENTE.getDescricao().equalsIgnoreCase(estado)) {
            loadFromS3(plano.getLink()).ifPresent(p -> {
                sincronizarBlocosComPlanoClassico(p);
                builder.plano(p);
            });
        }

        return builder.build();
    }

    /**
     * Mapeia e sincroniza tanto a lista 'clinicalPlan' (multi-dias) como a lista 'blocks' (dia único)
     * para a estrutura 'plan' legada, garantindo que a resposta ao Frontend nunca vá vazia.
     */
    private void sincronizarBlocosComPlanoClassico(TrainingPlanResponse newPlan) {
        if (newPlan == null) return;

        // Se 'plan' já contiver exercícios válidos, nada a fazer
        if (newPlan.getPlan() != null && !newPlan.getPlan().isEmpty()) {
            return;
        }

        // 1. Prioridade: Sincronizar a partir de 'clinicalPlan' (Multi-Dias)
        if (newPlan.getClinicalPlan() != null && !newPlan.getClinicalPlan().isEmpty()) {
            List<TrainingDay> days = new ArrayList<>();

            for (ClinicalDay cDay : newPlan.getClinicalPlan()) {
                TrainingDay day = new TrainingDay();
                day.setDay(cDay.getDay());

                List<TrainingExercise> exercises = new ArrayList<>();
                if (cDay.getBlocks() != null) {
                    for (ClinicalBlock block : cDay.getBlocks()) {
                        if (block.getExercises() != null) {
                            for (ClinicalExercise cEx : block.getExercises()) {
                                TrainingExercise ex = TrainingExercise.builder()
                                        .order(cEx.getOrder())
                                        .name(cEx.getName())
                                        .muscleGroup("[" + block.getBlockName() + "] " + (cEx.getTargetFocus() != null ? cEx.getTargetFocus() : "Reabilitação"))
                                        .sets(cEx.getDosageRight() != null ? cEx.getDosageRight() : "3")
                                        .reps(cEx.getDosageLeft() != null ? cEx.getDosageLeft() : "10-12")
                                        .tempo(cEx.getTempoRpe())
                                        .details(cEx.getExecutionInstructions())
                                        .notas(cEx.getSafetyNotes())
                                        .videoUrl(cEx.getVideoUrl())
                                        .build();
                                exercises.add(ex);
                            }
                        }
                    }
                }
                day.setExercises(exercises);
                days.add(day);
            }
            newPlan.setPlan(days);
            return;
        }

        // 2. Fallback: Sincronizar a partir de 'blocks' (Dia Único)
        if (newPlan.getBlocks() != null && !newPlan.getBlocks().isEmpty()) {
            List<TrainingDay> days = new ArrayList<>();

            for (ClinicalBlock block : newPlan.getBlocks()) {
                TrainingDay day = new TrainingDay();
                day.setDay(block.getBlockName() + (block.getDurationText() != null ? " (" + block.getDurationText() + ")" : ""));

                List<TrainingExercise> exercises = new ArrayList<>();
                if (block.getExercises() != null) {
                    for (ClinicalExercise cEx : block.getExercises()) {
                        TrainingExercise ex = TrainingExercise.builder()
                                .order(cEx.getOrder())
                                .name(cEx.getName())
                                .muscleGroup(cEx.getTargetFocus())
                                .sets(cEx.getDosageRight() != null ? cEx.getDosageRight() : "3")
                                .reps(cEx.getDosageLeft() != null ? cEx.getDosageLeft() : "10-12")
                                .tempo(cEx.getTempoRpe())
                                .details(cEx.getExecutionInstructions())
                                .notas(cEx.getSafetyNotes())
                                .videoUrl(cEx.getVideoUrl())
                                .build();
                        exercises.add(ex);
                    }
                }
                day.setExercises(exercises);
                days.add(day);
            }
            newPlan.setPlan(days);
        }
    }

    private List<String> carregarExerciciosParaEvitar(Optional<PlanoResponseDTO> planoAnteriorOpt) {
        List<String> exerciciosParaEvitar = new ArrayList<>();
        planoAnteriorOpt.ifPresent(plano -> {
            Optional<TrainingPlanResponse> antigo = loadFromS3(plano.getLink());
            antigo.ifPresent(oldPlan -> {
                sincronizarBlocosComPlanoClassico(oldPlan);
                if (oldPlan.getPlan() != null) {
                    List<String> names = oldPlan.getPlan().stream()
                            .flatMap(day -> day.getExercises().stream())
                            .map(TrainingExercise::getName)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    exerciciosParaEvitar.addAll(names);
                }
            });
        });
        return exerciciosParaEvitar;
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
            } catch (Exception ignored) {
            }
        }

        salvarDadosDoPlano(username, newPlan.getUserProfile(), key, newPlan, planId, true, semanaCiclo, isDeload);
    }

    @Override
    @Transactional
    public void associarPlanoAConta(String planId, String novoUsername) {
        PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
        if (plano == null) throw new RuntimeException("Plano não encontrado: " + planId);

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
    }

    @Override
    @Transactional
    public void saveProgressLogs(List<TrainingExercise> logs, String username, String planId) {
        if (logs == null || logs.isEmpty()) return;

        List<ExerciseHistoryEntity> entities = logs.stream().map(item -> ExerciseHistoryEntity.builder()
                .username(username)
                .planId(planId)
                .exerciseName(item.getName())
                .muscleGroup(item.getMuscleGroup())
                .weight(item.getWeight())
                .registeredAt(LocalDateTime.now())
                .clientDate(item.getDate())
                .build()).collect(Collectors.toList());

        exerciseHistoryRepository.saveAll(entities);
    }

    @Override
    public List<ExerciseHistoryResponse> getProgressLogs(String exerciseName, String username) {
        var entities = exerciseHistoryRepository.findByUsernameAndExerciseNameOrderByRegisteredAtDesc(username, exerciseName);
        return exerciseHistoryMapper.toResponseList(entities);
    }

    // Os nomes loadFromS3/saveToS3 mantêm-se por causa da interface TrainingPlanService
    // (e dos controllers que a usam). Por dentro: lê do S3 e, se der erro ou não existir, da BD.
    @Override
    public Optional<TrainingPlanResponse> loadFromS3(String key) {
        Optional<TrainingPlanResponse> response = planoStorage.load(key);
        response.ifPresent(this::sincronizarBlocosComPlanoClassico);
        return response;
    }

    @Override
    public void saveToS3(String key, TrainingPlanResponse plan) {
        planoStorage.save(key, plan);
    }

    private int calcularProximaSemanaCiclo(Optional<PlanoResponseDTO> planoAnteriorOpt, String identificador) {
        if (planoAnteriorOpt.isEmpty()) return 1;
        PlanoResponseDTO anterior = planoAnteriorOpt.get();
        String dataReferenciaStr = (anterior.getDataUpdate() != null && !anterior.getDataUpdate().isBlank())
                ? anterior.getDataUpdate() : anterior.getDataCreate();

        if (dataReferenciaStr == null || dataReferenciaStr.isBlank()) return 1;

        try {
            LocalDate dataReferencia = LocalDate.parse(dataReferenciaStr);
            long diasDesdeUltimoPlano = ChronoUnit.DAYS.between(dataReferencia, LocalDate.now());
            if (diasDesdeUltimoPlano > DIAS_LIMITE_PARA_REINICIAR_CICLO) return 1;
            return anterior.getSemanaCiclo() + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private String buscarChaveDoPlano(String planId, String username, UserProfileRequest request) {
        if (planId != null && !planId.isBlank()) {
            try {
                PlanoResponseDTO plano = planoService.getByPlanoById(UUID.fromString(planId));
                if (plano != null && isLinkValido(plano.getLink())) return plano.getLink();
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (request == null) {
            return planoService.findAtivoAndConcluidoByUsername(username)
                    .map(PlanoResponseDTO::getLink)
                    .filter(this::isLinkValido)
                    .orElseGet(() -> gerarCaminhoPadrao(username));
        } else {
            return gerarCaminhoPadrao(username);
        }
    }

    private boolean isLinkValido(String link) {
        return link != null && !link.isBlank();
    }

    private String gerarCaminhoPadrao(String username) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s%s/plan/%s_%s.json", S3FOLDER, username, username, timestamp);
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
                    .estadoPedido(Enum.EstadoPedido.FINALIZADO)
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
            if (planoExistente == null) throw new RuntimeException("Plano não encontrado para o ID: " + planId);

            boolean temNomeAluno = request.getStudentName() != null && !request.getStudentName().isBlank();
            String especialistaAtualizado = temNomeAluno ? "Sem Especialista" : username;

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