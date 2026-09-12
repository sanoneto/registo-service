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

    // Se o aluno passar mais do que este número de dias sem gerar um plano novo,
    // reiniciamos o ciclo de periodização na semana 1 em vez de continuar a
    // contagem antiga — evita cair diretamente numa semana de deload (ou
    // assumir progressão) depois de uma pausa longa.
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
        // 1. Determina a chave onde o plano está (ou estará) guardado
        String key = buscarChaveDoPlano(planId, username, request);
        log.info("Chave determinada: {}", key);

        // 2. CASO A: Pedido de LEITURA (Request vazio)
        // Se o request for vazio, apenas tentamos carregar o que já existe.
        if (isRequestEmpty(request)) {
            log.info("Request vazio. A carregar plano existente do S3...");
            return loadFromS3(key).orElse(null);
        }

        // 3. CASO B: Pedido de GERAÇÃO (Request com dados)
        log.info("Novo pedido de geração detetado. A verificar histórico para evitar repetição...");

        // 3.1. Busca o plano ativo/concluído anterior (usado para a "Lista Negra"
        // de exercícios E para calcular a semana do ciclo de periodização)
        Optional<PlanoResponseDTO> planoAnteriorOpt = planoService.findAtivoAndConcluidoByUsername(username);

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

        // 3.2. PERIODIZAÇÃO: calcula em que semana do ciclo este novo plano entra
        int weekNumber = calcularProximaSemanaCiclo(planoAnteriorOpt, username);
        boolean isDeloadWeek = weekNumber % 4 == 0;
        log.info("Utilizador {} — semana de ciclo calculada: {} (deload: {})", username, weekNumber, isDeloadWeek);

        // 3.3. Chamar a IA com o histórico extraído e a semana calculada
        TrainingPlanResponse newPlan = training.generateTrainingPlan(request, exerciciosParaEvitar, weekNumber);

        // 4. Configurar e Persistir
        configurarNovoPlano(newPlan, request);
        salvarDadosDoPlano(username, request, key, newPlan, planId, false, weekNumber, isDeloadWeek);

        return newPlan;
    }

    @Override
    public void updatePlan(TrainingPlanResponse newPlan, String username, String planId) {
        String key = buscarChaveDoPlano(planId, username, null);
        configurarNovoPlano(newPlan, newPlan.getUserProfile());

        // Isto NÃO é uma nova geração — mantém a semana/deload já registados
        // no plano existente, em vez de recalcular.
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

        // 4. Persistência (Banco e S3)
        salvarDadosDoPlano(username, newPlan.getUserProfile(), key, newPlan, planId, true, semanaCiclo, isDeload);
    }

    @Override
    @Transactional
    public void saveProgressLogs(List<TrainingExercise> logs, String username, String planId) {
        if (logs == null || logs.isEmpty()) return;

        // Converte os DTOs em Entidades e guarda
        List<ExerciseHistoryEntity> entities = logs.stream().map(log -> {
            return ExerciseHistoryEntity.builder()
                    .username(username)
                    .planId(planId)
                    .exerciseName(log.getName())
                    .muscleGroup(log.getMuscleGroup())
                    .weight(log.getWeight())
                    .registeredAt(LocalDateTime.now()) // Data do servidor para segurança
                    .clientDate(log.getDate()) // Data que veio do telemóvel do aluno
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

            // Verifica se o plano tem menos de 7 dias (opcional)
            Instant lastModified = s3Object.response().lastModified();
            return Optional.of(objectMapper.readValue(s3Object, TrainingPlanResponse.class));
        } catch (Exception e) {
            return Optional.empty(); // Arquivo não existe ou erro na leitura
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


    // --- Métodos Auxiliares para Limpar o Fluxo Principal ---

    /**
     * PERIODIZAÇÃO: calcula a semana do ciclo de treino a usar no próximo
     * plano gerado. Baseia-se no plano ATIVO+FINALIZADO anterior do aluno:
     * - Se não existir plano anterior -> semana 1 (início do ciclo).
     * - Se o aluno esteve sem gerar plano novo por mais de
     *   DIAS_LIMITE_PARA_REINICIAR_CICLO dias -> reinicia na semana 1
     *   (evita cair diretamente numa semana de deload após uma pausa longa).
     * - Caso contrário -> semanaCiclo do plano anterior + 1.
     */
    private int calcularProximaSemanaCiclo(Optional<PlanoResponseDTO> planoAnteriorOpt, String username) {
        if (planoAnteriorOpt.isEmpty()) {
            return 1;
        }

        PlanoResponseDTO anterior = planoAnteriorOpt.get();

        // dataUpdate reflete a última vez que este plano foi tocado (criação ou
        // atualização); usamos como referência de "quando o aluno gerou/usou
        // pela última vez um plano". Se vier vazio, cai para dataCreate.
        String dataReferenciaStr = (anterior.getDataUpdate() != null && !anterior.getDataUpdate().isBlank())
                ? anterior.getDataUpdate() : anterior.getDataCreate();

        if (dataReferenciaStr == null || dataReferenciaStr.isBlank()) {
            log.warn("Plano anterior de {} sem data de referência válida — a reiniciar ciclo.", username);
            return 1;
        }

        try {
            LocalDate dataReferencia = LocalDate.parse(dataReferenciaStr);
            long diasDesdeUltimoPlano = ChronoUnit.DAYS.between(dataReferencia, LocalDate.now());

            if (diasDesdeUltimoPlano > DIAS_LIMITE_PARA_REINICIAR_CICLO) {
                log.info("Utilizador {} esteve {} dias sem gerar plano novo — a reiniciar ciclo de periodização.",
                        username, diasDesdeUltimoPlano);
                return 1;
            }

            return anterior.getSemanaCiclo() + 1;
        } catch (Exception e) {
            log.warn("Não foi possível interpretar a data de referência ('{}') do plano anterior de {} — a reiniciar ciclo.",
                    dataReferenciaStr, username);
            return 1;
        }
    }

    private String buscarChaveDoPlano(String planId, String username, UserProfileRequest request) {
        // 1. Tentativa prioritária: Pelo UUID do plano (planId)
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

        // 2. Validação na Base de Dados: Procurar plano existente por Username + Filtros
        // O service deve buscar onde estado_plano = 'ATIVO' e estado_pedido = 'FINALIZADO'
        log.info(" Aprocura plano ativo e concluído na base para o utilizador: {}", username);
        if (request == null) {
            return planoService.findAtivoAndConcluidoByUsername(username)
                    .map(PlanoResponseDTO::getLink)
                    .filter(this::isLinkValido)
                    // 3. Fallback Final: Se não encontrar nada válido, gera o caminho padrão
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

    // Criado um método auxiliar para evitar repetição de código
    private String gerarCaminhoPadrao(String username) {

        log.info("dentro de gerarCaminhoPadrao");
        // Define o formato: AnoMesDia_HoraMinutoSegundo
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
        plan.setIsExistingPlan(true); // Se entendi, isso marca que o arquivo passará a existir
        plan.setUserProfile(request);
    }

    private void salvarDadosDoPlano(String username, UserProfileRequest request, String key, TrainingPlanResponse plan,
                                    String planId, boolean update, int semanaCiclo, boolean isDeload) {
        PlanoRequestDTO dto;

        if (update) {
            planoService.prepararNovoPlanoAtivo(username);
        }

        if (planId == null || planId.isEmpty()) {
            // --- OTIMIZAÇÃO AQUI ---
            // Verificamos uma única vez se existe um nome de aluno no request
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
                    .build();
            planoService.createPlano(dto);
            // -----------------------
        } else {
            PlanoResponseDTO planoExistente = planoService.getByPlanoById(UUID.fromString(planId));

            if (planoExistente == null) {
                throw new RuntimeException("Plano não encontrado para o ID: " + planId);
            }

            dto = PlanoRequestDTO.builder()
                    .nomeAluno(planoExistente.getNomeAluno())
                    .objetivo(planoExistente.getObjetivo())
                    .especialista(username)
                    .estadoPlano(Enum.EstadoPlano.ATIVO)
                    .estadoPedido(Enum.EstadoPedido.FINALIZADO)
                    .link(key)
                    .recommended(planoExistente.getRecommended())
                    .semanaCiclo(semanaCiclo)
                    .deload(isDeload)
                    .build();
            planoService.updatePlano(planId, dto);
        }

        saveToS3(key, plan);
    }


}