package com.aneto.registo_horas_service.service.impl;

import com.aneto.registo_horas_service.dto.request.PlanoRequestDTO;
import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.mapper.ExerciseHistoryMapper;
import com.aneto.registo_horas_service.models.Enum.EstadoPedido;
import com.aneto.registo_horas_service.models.Enum.EstadoPlano;
import com.aneto.registo_horas_service.models.ExerciseHistoryEntity;
import com.aneto.registo_horas_service.models.Training.Training;
import com.aneto.registo_horas_service.repository.ExerciseHistoryRepository;
import com.aneto.registo_horas_service.service.PlanoService;
import com.aneto.registo_horas_service.service.TrainingPlanService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private static final int DIAS_LIMITE_PARA_REINICIAR_CICLO = 14;
    private static final int SEMANAS_POR_CICLO = 4; // a última semana do ciclo é de deload
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String SEM_ESPECIALISTA = "Sem Especialista";
    private static final String FOCO_PADRAO = "Reabilitação";
    private static final String SERIES_PADRAO = "3";
    private static final String REPS_PADRAO = "10-12";

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
    private String s3Folder;

    /** Dados necessários para gerar um novo plano (partilhados entre geração síncrona e assíncrona). */
    private record ContextoGeracao(List<String> exerciciosParaEvitar, int semanaCiclo, boolean deload) {
    }

    // =========================================================================
    // Leitura / geração
    // =========================================================================

    @Override
    public TrainingPlanResponse getOrGeneratePlan(UserProfileRequest request, String username, String planId) {
        long inicio = System.currentTimeMillis();
        String key = buscarChaveDoPlano(planId, username, request);

        // 1. Modo consulta (pedido sem dados suficientes): leitura direta, S3 com fallback para a BD
        if (isPerfilIncompleto(request)) {
            Optional<TrainingPlanResponse> loaded = loadFromS3(key);
            if (loaded.isEmpty()) {
                log.warn("[getOrGeneratePlan] Plano não encontrado (S3 nem BD) para a chave '{}'.", key);
            }
            return loaded.orElse(null);
        }

        // 2. Geração síncrona com IA
        ContextoGeracao ctx = prepararContextoGeracao(request, username);

        TrainingPlanResponse newPlan = training.generateTrainingPlan(
                request, ctx.exerciciosParaEvitar(), ctx.semanaCiclo());

        sincronizarBlocosComPlanoClassico(newPlan);
        configurarNovoPlano(newPlan, request);
        salvarDadosDoPlano(username, request, key, newPlan, planId, false, ctx.semanaCiclo(), ctx.deload());

        log.info("[getOrGeneratePlan] Plano gerado e guardado em {} ms", System.currentTimeMillis() - inicio);
        return newPlan;
    }

    @Override
    public boolean isPedidoDeGeracaoCompleto(UserProfileRequest request) {
        return !isPerfilIncompleto(request);
    }

    @Override
    public PlanoResponseDTO iniciarGeracaoAssincrona(UserProfileRequest request, String username, String planId) {
        if (isPerfilIncompleto(request)) {
            throw new IllegalStateException("Pedido vazio. Utilize a leitura direta do plano.");
        }

        String key = buscarChaveDoPlano(planId, username, request);
        ContextoGeracao ctx = prepararContextoGeracao(request, username);

        PlanoResponseDTO planoRegistado = planoRegistoHelper.upsert(username, request, key, planId,
                ctx.semanaCiclo(), ctx.deload(), EstadoPedido.A_PROCESSAR, null);

        trainingGenerationAsyncService.gerarEGuardarPlano(
                request, username, planoRegistado.getId(), key,
                ctx.semanaCiclo(), ctx.deload(), ctx.exerciciosParaEvitar());

        return planoRegistado;
    }

    @Override
    public PlanoStatusResponse getStatusDoPlano(String planId) {
        PlanoResponseDTO plano = buscarPlanoPorId(planId).orElse(null);
        if (plano == null) return null;

        PlanoStatusResponse.PlanoStatusResponseBuilder builder = PlanoStatusResponse.builder()
                .planId(plano.getId())
                .estadoPedido(plano.getEstadoPedido())
                .erroMensagem(plano.getErroMensagem());

        // O plano pode ser carregado se estiver FINALIZADO ou PENDENTE.
        // loadFromS3 já sincroniza os blocos com a estrutura legada.
        if (planoDisponivelParaLeitura(plano.getEstadoPedido()) && isLinkValido(plano.getLink())) {
            loadFromS3(plano.getLink()).ifPresent(builder::plano);
        }

        return builder.build();
    }

    private ContextoGeracao prepararContextoGeracao(UserProfileRequest request, String username) {
        Optional<PlanoResponseDTO> planoAnterior = isAlunoSemConta(request)
                ? planoService.findAtivoAndConcluidoByAlunoTempId(request.getAlunoTempId())
                : planoService.findAtivoAndConcluidoByUsername(username);

        int semanaCiclo = calcularProximaSemanaCiclo(planoAnterior);
        return new ContextoGeracao(
                carregarExerciciosParaEvitar(planoAnterior),
                semanaCiclo,
                semanaCiclo % SEMANAS_POR_CICLO == 0);
    }

    private boolean planoDisponivelParaLeitura(String estadoPedido) {
        return EstadoPedido.FINALIZADO.getDescricao().equalsIgnoreCase(estadoPedido)
                || EstadoPedido.PENDENTE.getDescricao().equalsIgnoreCase(estadoPedido);
    }

    // =========================================================================
    // Conversão clinicalPlan/blocks -> plan (legado)
    // =========================================================================

    /**
     * Mapeia a lista 'clinicalPlan' (multi-dias) para a estrutura 'plan' legada,
     * garantindo que a resposta ao Frontend nunca vá vazia.
     */
    private void sincronizarBlocosComPlanoClassico(TrainingPlanResponse plan) {
        if (plan == null || !nullSafe(plan.getPlan()).isEmpty()) return; // já tem exercícios válidos
        if (nullSafe(plan.getClinicalPlan()).isEmpty()) return;

        plan.setPlan(plan.getClinicalPlan().stream()
                .map(this::converterDia)
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    private TrainingDay converterDia(ClinicalDay clinicalDay) {
        TrainingDay day = new TrainingDay();
        day.setDay(clinicalDay.getDay());
        day.setExercises(clinicalDay.getExercises());
        return day;
    }


    private List<String> carregarExerciciosParaEvitar(Optional<PlanoResponseDTO> planoAnterior) {
        return planoAnterior
                .filter(p -> isLinkValido(p.getLink()))
                .flatMap(p -> loadFromS3(p.getLink())) // já sincroniza com a estrutura legada
                .map(antigo -> nullSafe(antigo.getPlan()).stream()
                        .flatMap(day -> nullSafe(day.getExercises()).stream())
                        .map(TrainingExercise::getName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new)))
                .orElseGet(ArrayList::new);
    }

    // =========================================================================
    // Atualização / associação
    // =========================================================================

    @Override
    public void updatePlan(TrainingPlanResponse newPlan, String username, String planId) {
        UserProfileRequest perfil = newPlan.getUserProfile();
        if (perfil == null) {
            throw new IllegalArgumentException("O plano a atualizar não contém o perfil do utilizador.");
        }

        String key = buscarChaveDoPlano(planId, username, null);
        configurarNovoPlano(newPlan, perfil);

        Optional<PlanoResponseDTO> existente = buscarPlanoPorId(planId);
        int semanaCiclo = existente.map(PlanoResponseDTO::getSemanaCiclo).orElse(1);
        boolean deload = existente.map(PlanoResponseDTO::isDeload).orElse(false);

        salvarDadosDoPlano(username, perfil, key, newPlan, planId, true, semanaCiclo, deload);
    }

    @Override
    @Transactional
    public void associarPlanoAConta(String planId, String novoUsername) {
        PlanoResponseDTO plano = buscarPlanoPorId(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plano não encontrado: " + planId));

        PlanoRequestDTO dto = PlanoRequestDTO.builder()
                .nomeAluno(novoUsername)
                .objetivo(plano.getObjetivo())
                .especialista(plano.getEspecialista())
                .estadoPlano(EstadoPlano.valueOf(plano.getEstadoPlano()))
                .estadoPedido(EstadoPedido.fromDescricao(plano.getEstadoPedido()))
                .link(plano.getLink())
                .recommended(plano.getRecommended())
                .semanaCiclo(plano.getSemanaCiclo())
                .deload(plano.isDeload())
                .contaAssociada(true)
                .alunoTempId(null)
                .build();

        planoService.updatePlano(planId, dto);
    }

    // =========================================================================
    // Histórico de progresso
    // =========================================================================

    @Override
    @Transactional
    public void saveProgressLogs(List<TrainingExercise> logs, String username, String planId) {
        if (logs == null || logs.isEmpty()) return;

        LocalDateTime agora = LocalDateTime.now();
        List<ExerciseHistoryEntity> entities = logs.stream()
                .map(item -> ExerciseHistoryEntity.builder()
                        .username(username)
                        .planId(planId)
                        .exerciseName(item.getName())
                        .muscleGroup(item.getMuscleGroup())
                        .cargaSugerida(item.getCargaSugerida())
                        .registeredAt(agora)
                        .clientDate(item.getDate())
                        .build())
                .toList();

        exerciseHistoryRepository.saveAll(entities);
    }

    @Override
    public List<ExerciseHistoryResponse> getProgressLogs(String exerciseName, String username) {
        var entities = exerciseHistoryRepository
                .findByUsernameAndExerciseNameOrderByRegisteredAtDesc(username, exerciseName);
        return exerciseHistoryMapper.toResponseList(entities);
    }

    // =========================================================================
    // Storage
    // =========================================================================

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

    // =========================================================================
    // Ciclo de treino
    // =========================================================================

    private int calcularProximaSemanaCiclo(Optional<PlanoResponseDTO> planoAnterior) {
        return planoAnterior.map(this::proximaSemana).orElse(1);
    }

    private int proximaSemana(PlanoResponseDTO anterior) {
        String dataReferenciaStr = isLinkValido(anterior.getDataUpdate())
                ? anterior.getDataUpdate()
                : anterior.getDataCreate();

        if (dataReferenciaStr == null || dataReferenciaStr.isBlank()) return 1;

        try {
            long dias = ChronoUnit.DAYS.between(LocalDate.parse(dataReferenciaStr), LocalDate.now());
            return dias > DIAS_LIMITE_PARA_REINICIAR_CICLO ? 1 : anterior.getSemanaCiclo() + 1;
        } catch (DateTimeParseException e) {
            log.warn("[proximaSemana] Data de referência inválida '{}'. O ciclo será reiniciado.", dataReferenciaStr);
            return 1;
        }
    }

    // =========================================================================
    // Persistência do plano (BD + storage)
    // =========================================================================

    private void salvarDadosDoPlano(String username, UserProfileRequest request, String key,
                                    TrainingPlanResponse plan, String planId, boolean update,
                                    int semanaCiclo, boolean deload) {
        // 1. Conteúdo primeiro: se falhar, o registo nunca aponta para um ficheiro inexistente
        //    e o plano ativo anterior mantém-se. Um órfão no storage é inofensivo.
        saveToS3(key, plan);

        // 2. Só depois o registo na BD
        if (update) {
            if (isAlunoSemConta(request)) {
                planoService.prepararNovoPlanoAtivoPorAlunoTempId(request.getAlunoTempId());
            } else {
                planoService.prepararNovoPlanoAtivo(username);
            }
        }

        if (planId == null || planId.isBlank()) {
            criarRegistoDoPlano(username, request, key, semanaCiclo, deload);
        } else {
            atualizarRegistoDoPlano(planId, username, request, key, semanaCiclo, deload);
        }
    }

    private void criarRegistoDoPlano(String username, UserProfileRequest request, String key,
                                     int semanaCiclo, boolean deload) {
        boolean semConta = isAlunoSemConta(request);
        boolean temNomeAluno = temNomeAluno(request);

        PlanoRequestDTO dto = PlanoRequestDTO.builder()
                .nomeAluno(temNomeAluno ? request.getStudentName() : username)
                .objetivo(request.getObjective())
                .especialista(resolverEspecialista(request, username))
                .estadoPlano(EstadoPlano.ATIVO)
                .estadoPedido(EstadoPedido.FINALIZADO)
                .link(key)
                .recommended(temNomeAluno ? request.getRecommended() : username)
                .semanaCiclo(semanaCiclo)
                .deload(deload)
                .contaAssociada(!semConta)
                .alunoTempId(semConta ? request.getAlunoTempId() : null)
                .build();

        planoService.createPlano(dto);
    }

    private void atualizarRegistoDoPlano(String planId, String username, UserProfileRequest request, String key,
                                         int semanaCiclo, boolean deload) {
        PlanoResponseDTO existente = buscarPlanoPorId(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plano não encontrado para o ID: " + planId));

        String especialista = resolverEspecialista(request, username);

        PlanoRequestDTO dto = PlanoRequestDTO.builder()
                .nomeAluno(existente.getNomeAluno())
                .objetivo(existente.getObjetivo())
                .especialista(especialista)
                .estadoPlano(EstadoPlano.ATIVO)
                .estadoPedido(EstadoPedido.FINALIZADO)
                .link(key)
                .recommended(existente.getRecommended())
                .semanaCiclo(semanaCiclo)
                .deload(deload)
                .contaAssociada(existente.isContaAssociada())
                .alunoTempId(existente.getAlunoTempId())
                .build();

        planoService.updatePlano(planId, dto);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Optional<PlanoResponseDTO> buscarPlanoPorId(String planId) {
        if (planId == null || planId.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(planoService.getByPlanoById(UUID.fromString(planId)));
        } catch (IllegalArgumentException e) {
            log.warn("[buscarPlanoPorId] planId inválido: '{}'", planId);
            return Optional.empty();
        }
    }

    private String buscarChaveDoPlano(String planId, String username, UserProfileRequest request) {
        Optional<String> linkDoPlano = buscarPlanoPorId(planId)
                .map(PlanoResponseDTO::getLink)
                .filter(this::isLinkValido);
        if (linkDoPlano.isPresent()) return linkDoPlano.get();

        if (request != null) return gerarCaminhoPadrao(username);

        return planoService.findAtivoAndConcluidoByUsername(username)
                .map(PlanoResponseDTO::getLink)
                .filter(this::isLinkValido)
                .orElseGet(() -> gerarCaminhoPadrao(username));
    }

    private String gerarCaminhoPadrao(String username) {
        String pasta = s3Folder.endsWith("/") ? s3Folder : s3Folder + "/";
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return String.format("%s%s/plan/%s_%s.json", pasta, username, username, timestamp);
    }

    /** Perfil insuficiente para gerar um plano => o pedido é só de consulta. */
    private boolean isPerfilIncompleto(UserProfileRequest request) {
        return request == null
                || isBlank(request.getObjective())
                || request.getWeightKg() == null
                || request.getHeightCm() == null
                || request.getAge() == null;
    }

    private void configurarNovoPlano(TrainingPlanResponse plan, UserProfileRequest request) {
        plan.setIsExistingPlan(true);
        plan.setUserProfile(request);
    }

    private boolean isAlunoSemConta(UserProfileRequest request) {
        return request != null && !isBlank(request.getAlunoTempId());
    }

    /** Com nome de aluno, quem pede é o especialista; caso contrário o plano é pessoal. */
    private String resolverEspecialista(UserProfileRequest request, String username) {
        return temNomeAluno(request) ? username : SEM_ESPECIALISTA;
    }

    private boolean temNomeAluno(UserProfileRequest request) {
        return request != null && !isBlank(request.getStudentName());
    }

    private boolean isLinkValido(String link) {
        return !isBlank(link);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}