package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.models.Enum;
import com.aneto.registo_horas_service.service.ExerciseVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class Training {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ExerciseVideoService exerciseVideoService;

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest, List<String> exerciciosDoS3) {

        log.info("Iniciando generateTrainingPlan para o utilizador.");

        // 1. Sanitização de entradas
        String exerciseHistoryText = defaultIfEmpty(userRequest.getExerciseHistory(), "Não informado");
        String objectiveText = defaultIfEmpty(userRequest.getObjective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.getLocation(), "Não especificada");
        String bodyTypeText = (userRequest.getBodyType() != null) ? userRequest.getBodyType().name() : "ECTOMORPH";
        String genderText = (userRequest.getGender() != null) ? userRequest.getGender().name() : "MALE";
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.getWeightKg()), "70");

        String durationText = (userRequest.getDurationPerSession() != null && !userRequest.getDurationPerSession().isBlank())
                ? userRequest.getDurationPerSession() : "60 minutos";

        String protocolId = (userRequest.getProtocol() == null) ? "nasm_estabilizacao" : userRequest.getProtocol();
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        int totalMinutos = extrairMinutosTotais(durationText);
        int volumeIdeal = Math.max(6, totalMinutos / 7);
        String pathologyText = (userRequest.getPathology() == null || userRequest.getPathology().isBlank()) ? "Nenhuma limitação relatada" : userRequest.getPathology();

        // 2. Cálculos Nutricionais e Macros
        Macros macros = MacroCalculator.calculate(
                userRequest.getWeightKg(),
                userRequest.getHeightCm(),
                userRequest.getAge(),
                genderText,
                bodyTypeText,
                userRequest.getBodyFat() != null ? userRequest.getBodyFat() : 15.0,
                userRequest.getMealsPerDay() != null ? userRequest.getMealsPerDay() : 6
        );

        // 3. Lógica de Variação (Anti-Platô) e NASM
        String tempoNASM = protocolId.contains("estabilizacao") ? "4-2-1" : "2-0-2";
        String listaParaEvitar = (exerciciosDoS3 == null || exerciciosDoS3.isEmpty())
                ? "Nenhum (Primeiro plano do aluno)"
                : String.join(", ", exerciciosDoS3);

        // --- DETEÇÃO DE FOCO ESPECÍFICO (EX: GLÚTEO) ---
        boolean isGluteFocus = objectiveText.toLowerCase().contains("glúteo") || objectiveText.toLowerCase().contains("gluteo");
        String diretrizFocoEspecial = isGluteFocus ? """
                [FOCO PRIORITÁRIO: GLÚTEOS]
                - O aluno deseja foco total em Glúteos.
                - 70% dos exercícios de membros inferiores devem ser específicos para Glúteos (Grande, Médio e Mínimo).
                - Incluir obrigatoriamente variações de Elevação Pélvica, Agachamento Búlgaro e Abduções.
                """ : "";

        String diretrizVariedade = """
                [SISTEMA DE VARIAÇÃO ANTI-PLATÔ]
                - EXERCÍCIOS JÁ REALIZADOS (PROIBIDO REPETIR): %s.
                - REGRA DE OURO: É estritamente proibido repetir o mesmo exercício em dias diferentes deste plano.
                - TEMA: Focar em variações biomecânicas.
                - RITMO OBRIGATÓRIO: O campo 'tempo' no JSON deve ser rigorosamente '%s'.
                """.formatted(listaParaEvitar, tempoNASM);

        // 4. Adaptação para Sedentários (Prevenção de mal-estar)
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.getExerciseHistory());
        String protocoloEfetivo = isSedentary ? "Adaptação Anatómica (Baixa Intensidade)" : protocol.getLabel();
        String repsEfetivas = isSedentary ? "12 a 15 (longe da falha)" : protocol.getReps();
        String setsEfetivas = isSedentary ? "2" : protocol.getSets();
        String descansoEfetivo = isSedentary ? "120" : calcularDescansoCientifico(protocol, objectiveText, weightKg);

        String diretrizSegurancaIniciante = isSedentary ? """
                [ALERTA DE SEGURANÇA: ALUNO SEDENTÁRIO]
                - O aluno nunca treinou. É TERMINANTEMENTE PROIBIDO levar à falha concêntrica.
                - Prioridade: Estabilidade hemodinâmica. Não usar superséries.
                - Evitar exercícios com a cabeça abaixo do nível do coração.
                - Foco em máquinas (maior estabilidade).
                """ : "";

        String detalhesEquipamento = locationText.equalsIgnoreCase("Casa") ?
                "UTILIZA APENAS: 'Peso Corporal', 'Halteres' ou 'Bandas Elásticas'. PROIBIDO o uso de máquinas de ginásio." :
                "UTILIZA: 'Máquinas', 'Barras', 'Polias' ou 'Halteres'.";

        String filtroEquipamento = isSedentary ?
                "PREFERÊNCIA OBRIGATÓRIA: Máquinas guiadas para maior controlo motor e segurança." :
                detalhesEquipamento;

        // --- BLOCOS DE DIRETRIZES TÉCNICAS ---

        String diretrizProtocolo = """
                DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):
                - Séries: %s | Repetições: %s
                - DESCANSO CIENTÍFICO: %s segundos fixos.
                - RITMO (Tempo): %s
                - JUSTIFICATIVA FISIOLÓGICA: O descanso de %s garante a resíntese de Fosfocreatina.
                """.formatted(protocoloEfetivo, setsEfetivas, repsEfetivas, descansoEfetivo, protocol.getTempo(), descansoEfetivo);

        String diretrizBiomecanica = """
                DIRETRIZES DE SELEÇÃO BIOMECÂNICA ESTREITAS:
                1. REPERTÓRIO: %s.
                2. PROIBIDOS: %s.
                3. ADAPTAÇÃO: Patologia "%s", substitui impactos por baixo torque.
                4. SEPARAÇÃO TOTAL: PROIBIDO incluir PERNAS em dias de SUPERIORES.
                5. COERÊNCIA: PUSH (Peito/Ombros/Tríceps), LEGS (Quadríceps/Isquios/Glúteos).
                """.formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = """
                [REGRA OBRIGATÓRIA DE AQUECIMENTO E MOBILIDADE]
                - Se houver patologia ("%s"), o exercício de "order": 1 DEVE ser de Reabilitação/Mobilidade focado nessa área (categoria REABILITAÇÃO do dicionário).
                - Se não houver patologia, o "order": 1 deve ser Mobilidade Geral, escolhida da categoria MOBILIDADE do dicionário.
                - RELAÇÃO COM O DIA: sempre que possível, escolhe o aquecimento relacionado com o grupo muscular treinado nesse dia. Ex: dia de COSTAS -> "Y-W-T" ou "Open Books" (mobilidade escapular/torácica); dia de PERNAS -> "Knee-to-Wall" ou "Cossack Squat"; dia de PEITO/OMBROS sem opção específica -> usa mobilidade geral como "Cat Cow".
                - VARIEDADE OBRIGATÓRIA: NÃO uses sempre o mesmo exercício de mobilidade em todos os dias.
                - É PROIBIDO usar o mesmo exercício de aquecimento em dois dias CONSECUTIVOS.
                """.formatted(pathologyText);

        String diretrizTreino = buildDiretrizTreino(userRequest, durationText, volumeIdeal);
        String diretrizAlimentar = buildDiretrizAlimentar(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Escolha exercícios que respeitem o protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.getFrequencyPerWeek());

        String diretrizEquipamento = """
                LOGÍSTICA E EQUIPAMENTO:
                - Localização: %s.
                - REGRA ABSOLUTA: O campo 'equipment' no JSON deve indicar o material.
                - %s
                - VÍDEOS: Usa obrigatoriamente nomes do DICIONÁRIO.
                """.formatted(locationText, filtroEquipamento);

        String diretrizArrefecimento = """
                REGRA DE ARREFECIMENTO:
                - O ÚLTIMO exercício de cada dia deve ser um exercício da categoria ALONGAMENTO do DICIONÁRIO OFICIAL, focado em %s.
                - RELAÇÃO COM O DIA: escolhe o alongamento relacionado com o grupo muscular treinado nesse dia. Ex: dia de COSTAS -> "Alongamento do músculo grande dorsal"; dia de PERNAS -> "Flexores da Anca", "Alongamento borboleta", "Walking Lunges with Reach" ou "Frankenstein Walk".
                - VARIEDADE OBRIGATÓRIA: NÃO uses sempre o mesmo exercício de arrefecimento em todos os dias.
                - É PROIBIDO usar o mesmo exercício de arrefecimento em dois dias CONSECUTIVOS.
                """.formatted(pathologyText);

        String diretrizMobilidadeCondicional = """
                [REGRA DE OURO: MOBILIDADE ESPECÍFICA SÓ COM QUEIXA CORRESPONDENTE]
                - Exercícios de mobilidade/reabilitação ESPECÍFICOS de uma articulação (ex: "Rotação Externa", "Clamshell") \
                só podem ser usados SE a patologia relatada mencionar explicitamente essa zona do corpo.
                - Patologia relatada pelo aluno: "%s".
                - Se a patologia for "Nenhuma limitação relatada" ou não mencionar uma articulação específica, \
                é PROIBIDO usar "Rotação Externa", "Clamshell" ou qualquer exercício de reabilitação dirigido a uma queixa que o aluno não tem.
                - Nesse caso (sem patologia), usa APENAS mobilidade GERAL: "Cat Cow", "Y-W-T", "Open Books", "Knee-to-Wall", "Cossack Squat" ou "Equilíbrio Unipodal".
                - Exemplo errado: aluno sem patologia recebe "Rotação Externa" -> ERRO CRÍTICO.
                - Exemplo correto: aluno com patologia "Ombro" recebe "Rotação Externa" como order 1 -> CORRETO.
                """.formatted(pathologyText);

        String diretrizNomenclaturaDias = buildDiretrizNomenclaturaDias(userRequest, isGluteFocus, pathologyText);

        String diretrizReabilitacao = pathologyText.contains("Nenhuma") ?
                "Foca o primeiro exercício em mobilidade geral ou ativação dinâmica." :
                buildDiretrizReabilitacao(pathologyText) + "\n- REGRA: Proibido repetir o mesmo exercício de reabilitação em dias consecutivos.";

        // --- DICIONÁRIO DE EXERCÍCIOS (BD com fallback estático) ---
        Map<String, List<String>> exerciseDictionary;
        try {
            exerciseDictionary = exerciseVideoService.getExerciseDictionary();
        } catch (Exception e) {
            log.error("Erro ao obter dicionário de exercícios da BD, a usar fallback estático: {}", e.getMessage());
            exerciseDictionary = Collections.emptyMap();
        }

        if (exerciseDictionary == null || exerciseDictionary.isEmpty()) {
            log.warn("Dicionário de exercícios da BD vazio ou indisponível — a usar fallback estático.");
            exerciseDictionary = FALLBACK_DICTIONARY;
        }

        String diretrizDicionario = buildDiretrizDicionario(exerciseDictionary);

        String diretrizAnatomiaDetalhada = """
                DETALHAMENTO ANATÓMICO:
                - No campo 'muscleGroup', lista: Motores Primários (Agonistas), Sinergistas e Estabilizadores.
                - Exemplo: "Peitoral Maior, Deltoide Anterior, Tríceps Braquial, Serrátil Anterior".
                """;

        String diretrizCargasDinamicas = """
                SISTEMA DINÂMICO DE CARGAS (RPE):
                - Histórico: "%s".
                - RPE Sugerido: Sedentário (4-5), Beginner (6-7), Intermediate (7-8), Advanced (9).
                - No campo 'cargaAtual', escreve a orientação RPE.
                """.formatted(exerciseHistoryText);

        String regrasFinais = """
                REGRAS CRÍTICAS DE FECHAMENTO:
                1. DURAÇÃO: O treino deve durar %d minutos. Gera EXATAMENTE %d exercícios por dia.
                2. RITMO E DESCANSO: Ritmo %s e Descanso %s segundos.
                3. FORMATO: Responde APENAS o JSON puro.
                4. TOTAIS DIETA: %d kcal, %dg Prot, %dg Carbs, %dg Fats.
                """.formatted(totalMinutos, volumeIdeal, protocol.getTempo(), descansoEfetivo, macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats());

        // --- CONSTRUÇÃO DO PROMPT FINAL (blocos vazios são filtrados) ---
        String blocoDiretrizesCompletas = Stream.of(
                        diretrizFocoEspecial, diretrizVariedade, diretrizAquecimento, diretrizMobilidadeCondicional,
                        diretrizSegurancaIniciante, diretrizProtocolo, diretrizReabilitacao, diretrizBiomecanica,
                        diretrizAnatomiaDetalhada, diretrizCargasDinamicas, diretrizEquipamento, diretrizTreino,
                        diretrizRepertorio, diretrizArrefecimento, diretrizNomenclaturaDias, diretrizDicionario,
                        diretrizAlimentar
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                ATUAÇÃO: Personal Trainer e Nutricionista Profissional (Portugal).
                PERFIL: %d anos, %s, %s, %.2fkg. Objetivo: %s. Patologias: %s.
                
                SISTEMA DE REGRAS TÉCNICAS:
                %s
                
                %s
                
                FORMATO JSON OBRIGATÓRIO:
                {
                "summary": "Explicação técnica da estratégia %s.",
                "plan": [
                    {
                    "day": "Dia X - [CATEGORIA]: [FOCO]",
                    "exercises": [
                        {
                        "order": 1, "name": "...",
                        "muscleGroup": "...", "movementPlane": "...", "equipment": "...",
                        "tempo": "%s", "sets": "3", "reps": "15", "rest": "%s",
                        "weight": "0kg",
                        "cargaAtual": "...",
                         "videoUrl": "",
                         "details": "Instrução técnica biomecânica focada na execução correta e plano de movimento.",
                        "notas": "Dica de conexão mente-músculo e segurança (Pista Mental).",
                        "date": "Data do Treino"
                        }
                      ]
                    }
                ],
                "dietPlan": {
                    "dailyCalories": %d, "imc": %.2f, "imcCategory": "%s",
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [{"time": "HH:mm", "description": "...", "ingredients": ["..."], "calories": 0, "protein": 0, "carbs": 0, "fats": 0}]
                  }
                }
                """.formatted(
                userRequest.getAge(), bodyTypeText, genderText, userRequest.getWeightKg(), objectiveText, pathologyText,
                blocoDiretrizesCompletas, regrasFinais,
                protocol.getLabel(), protocol.getTempo(), descansoEfetivo,
                macros.dailyCalories(), macros.imc(), macros.imcCategory(),
                macros.protein(), macros.carbs(), macros.fats()
        );

        return executeGeneration(userPrompt, totalMinutos, exerciseDictionary, pathologyText, exerciciosDoS3);
    }

    @NotNull
    private String buildDiretrizNomenclaturaDias(UserProfileRequest userRequest, boolean isGluteFocus, String pathologyText) {
        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus);

        StringBuilder listaDias = new StringBuilder();
        for (int i = 0; i < sequencia.size(); i++) {
            listaDias.append("   - Dia ").append(i + 1).append(" - ").append(sequencia.get(i)).append("\n");
        }

        return """
                REGRAS ESTRITAS DE DIVISÃO (FREQUÊNCIA %d DIAS):
                1. NOMENCLATURA OBRIGATÓRIA: Usa EXATAMENTE estes nomes de dia, NESTA ORDEM, sem alterar texto, categoria ou sequência:
                %s
                2. PROIBIDO REPETIR A MESMA CATEGORIA (PUSH/PULL/LEGS/SUPERIORES/INFERIORES) EM DIAS CONSECUTIVOS. A sequência acima já garante isso — não a modifiques.
                3. REABILITAÇÃO DIÁRIA (OBRIGATÓRIO): O primeiro exercício (Order 1) de TODOS os dias deve ser obrigatoriamente para %s.
                4. VARIABILIDADE: Proibido repetir exercícios entre os dias. Cada bloco deve ter 100%% de exercícios únicos.
                5. ESTRUTURA: Gere exatamente %d blocos dentro do array "plan", correspondendo 1:1 à lista acima.
                """.formatted(userRequest.getFrequencyPerWeek(), listaDias, pathologyText, userRequest.getFrequencyPerWeek());
    }

    /**
     * Executa a geração do plano via IA, com validação estrita do inventário
     * de exercícios e retries com feedback específico do erro.
     */

    private TrainingPlanResponse executeGeneration(
            String prompt, int totalMinutos, Map<String, List<String>> exerciseDictionary,
            String pathologyText, List<String> exerciciosAnteriores) {

        log.info("Iniciando executeGeneration no ChatModel.");
        int maxRetries = 5;

        // Calculado uma única vez por chamada (não por exercício) para eficiência
        Set<String> validNames = flattenDictionary(
                (exerciseDictionary == null || exerciseDictionary.isEmpty())
                        ? FALLBACK_DICTIONARY : exerciseDictionary
        );

        // --- Opções disponíveis por categoria (para variedade e correspondência de grupo) ---
        Set<String> categoriasAquecimento = Set.of("MOBILIDADE", "REABILITAÇÃO");
        List<String> opcoesAquecimento = exerciseDictionary.entrySet().stream()
                .filter(e -> categoriasAquecimento.stream().anyMatch(cat -> e.getKey().equalsIgnoreCase(cat)))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
        boolean exigirVariedadeAquecimento = opcoesAquecimento.size() >= 2;

        Set<String> categoriasArrefecimento = Set.of("ALONGAMENTO");
        List<String> opcoesArrefecimento = exerciseDictionary.entrySet().stream()
                .filter(e -> categoriasArrefecimento.stream().anyMatch(cat -> e.getKey().equalsIgnoreCase(cat)))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
        boolean exigirVariedadeArrefecimento = opcoesArrefecimento.size() >= 2;

        log.info("Variedade disponível — Aquecimento: {} opções (exigir variedade: {}) | Arrefecimento: {} opções (exigir variedade: {})",
                opcoesAquecimento.size(), exigirVariedadeAquecimento, opcoesArrefecimento.size(), exigirVariedadeArrefecimento);

        // Normaliza a lista de exercícios do plano anterior para comparação consistente
        Set<String> nomesAnteriores = (exerciciosAnteriores == null ? List.<String>of() : exerciciosAnteriores)
                .stream()
                .map(this::normalizeExerciseName)
                .collect(Collectors.toCollection(HashSet::new));

        StringBuilder promptBuilder = new StringBuilder(prompt);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String textResponse = chatModel.call(promptBuilder.toString());
                String cleanedJson = cleanMarkdown(textResponse);

                // 1. DESSERIALIZAÇÃO INICIAL
                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // 2. VALIDAÇÃO DE VOLUME
                int minExAceitavel = Math.max(4, (totalMinutos / 10));
                boolean isVolumeValido = response.getPlan() != null && !response.getPlan().isEmpty() &&
                        response.getPlan().stream().allMatch(d -> d.getExercises() != null && d.getExercises().size() >= minExAceitavel);

                if (!isVolumeValido) throw new RuntimeException("Volume insuficiente.");

                // 3. VALIDAÇÃO DE SEQUÊNCIA (sem repetição de categoria em dias consecutivos)
                String categoriaAnterior = null;
                for (TrainingDay day : response.getPlan()) {
                    String categoriaAtual = extrairCategoriaDia(day.getDay());
                    if (categoriaAtual != null && categoriaAtual.equalsIgnoreCase(categoriaAnterior)) {
                        throw new RuntimeException(
                                "Repetição de categoria de treino em dias consecutivos: \"" + categoriaAtual +
                                        "\" apareceu duas vezes seguidas (dia \"" + day.getDay() + "\"). " +
                                        "Segue rigorosamente a ordem de dias fornecida nas diretrizes de nomenclatura, sem repetir a mesma categoria em dias seguidos."
                        );
                    }
                    categoriaAnterior = categoriaAtual;
                }

                // 4. MAPEAMENTO + VALIDAÇÃO DE INVENTÁRIO + UNICIDADE/VARIEDADE + CORRESPONDÊNCIA DE GRUPO
                List<TrainingDay> updatedPlan = new ArrayList<>();
                Set<String> nomesUsadosNoPlano = new HashSet<>(); // apenas para exercícios de TRABALHO (meio do treino)
                String aquecimentoAnterior = null;
                String arrefecimentoAnterior = null;
                boolean pathologyEspecifica = pathologyText != null && !pathologyText.contains("Nenhuma");

                for (TrainingDay day : response.getPlan()) {
                    List<TrainingExercise> exercisesOfDay = day.getExercises() != null
                            ? day.getExercises() : Collections.emptyList();

                    List<TrainingExercise> enrichedExercises = new ArrayList<>();
                    int totalExDoDia = exercisesOfDay.size();
                    Set<String> gruposDoDia = extrairGruposMuscularesDoDia(day.getDay());

                    for (int i = 0; i < totalExDoDia; i++) {
                        TrainingExercise ex = exercisesOfDay.get(i);
                        TrainingExercise enriched = enrichExercise(ex, validNames, pathologyText);

                        boolean isAquecimento = (i == 0);
                        boolean isArrefecimento = (i == totalExDoDia - 1);

                        if (isAquecimento) {
                            if (!pathologyEspecifica) {
                                // 4a. Variedade: não repetir em dias consecutivos
                                if (exigirVariedadeAquecimento && enriched.getName().equalsIgnoreCase(aquecimentoAnterior)) {
                                    throw new RuntimeException(
                                            "Exercício de aquecimento repetido em dias consecutivos: \"" + enriched.getName() +
                                                    "\". Escolhe um exercício de mobilidade diferente do DICIONÁRIO para este dia (há várias opções disponíveis)."
                                    );
                                }
                                // 4b. Correspondência de grupo muscular com o foco do dia
                                if (!grupoMuscularCorresponde(enriched.getName(), gruposDoDia)
                                        && existeOpcaoMelhorParaGrupo(gruposDoDia, opcoesAquecimento, enriched.getName(), pathologyText)) {
                                    throw new RuntimeException(
                                            "Aquecimento desalinhado com o foco do dia: \"" + enriched.getName() +
                                                    "\" não é adequado ao dia \"" + day.getDay() + "\". " +
                                                    "Escolhe um exercício de mobilidade do DICIONÁRIO relacionado com o grupo muscular treinado nesse dia."
                                    );
                                }
                            }
                            aquecimentoAnterior = enriched.getName();

                        } else if (isArrefecimento) {
                            // 4c. Variedade: não repetir em dias consecutivos
                            if (exigirVariedadeArrefecimento && enriched.getName().equalsIgnoreCase(arrefecimentoAnterior)) {
                                throw new RuntimeException(
                                        "Exercício de arrefecimento repetido em dias consecutivos: \"" + enriched.getName() +
                                                "\". Escolhe um exercício de alongamento diferente do DICIONÁRIO para este dia."
                                );
                            }
                            // 4d. Correspondência de grupo muscular com o foco do dia
                            if (!grupoMuscularCorresponde(enriched.getName(), gruposDoDia)
                                    && existeOpcaoMelhorParaGrupo(gruposDoDia, opcoesArrefecimento, enriched.getName(), pathologyText)) {
                                throw new RuntimeException(
                                        "Arrefecimento desalinhado com o foco do dia: \"" + enriched.getName() +
                                                "\" não é adequado ao dia \"" + day.getDay() + "\". " +
                                                "Escolhe um exercício de alongamento do DICIONÁRIO relacionado com o grupo muscular treinado nesse dia."
                                );
                            }
                            arrefecimentoAnterior = enriched.getName();

                        } else {
                            // Exercícios de TRABALHO: unicidade total no plano + anti-platô vs plano anterior
                            if (!nomesUsadosNoPlano.add(enriched.getName())) {
                                throw new RuntimeException(
                                        "Exercício repetido no plano: \"" + enriched.getName() +
                                                "\" já foi usado noutro dia. É proibido repetir o mesmo exercício de TRABALHO em dias diferentes " +
                                                "(aquecimento e arrefecimento seguem regras próprias). Substitui por uma variação diferente da mesma categoria muscular."
                                );
                            }
                            if (nomesAnteriores.contains(enriched.getName())) {
                                throw new RuntimeException(
                                        "Exercício repetido do plano anterior: \"" + enriched.getName() +
                                                "\" já foi usado no plano anterior deste aluno e deve ser evitado (anti-platô). " +
                                                "Substitui por uma variação diferente da mesma categoria muscular."
                                );
                            }
                        }

                        enrichedExercises.add(enriched);
                    }

                    updatedPlan.add(new TrainingDay(day.getDay(), enrichedExercises));
                }

                // 5. LIMPEZA DA DIETA (garante listas mutáveis)
                DietPlan diet = response.getDietPlan();
                if (diet != null && diet.getMeals() != null) {
                    List<Meal> mutableMeals = new ArrayList<>();
                    for (Meal m : diet.getMeals()) {
                        if (m.getIngredients() != null) {
                            m.setIngredients(new ArrayList<>(m.getIngredients()));
                        }
                        mutableMeals.add(m);
                    }
                    diet.setMeals(mutableMeals);
                }

                log.info("Geração concluída com sucesso, validada contra inventário, sequência de dias, variedade e correspondência de grupo muscular.");

                // 6. RESPOSTA FINAL
                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .dietPlan(diet)
                        .userProfile(null)
                        .build();

            } catch (Exception e) {
                log.warn("Falha na tentativa {}/{} - Erro: {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("Todas as tentativas falharam. Erro final: {}", e.getMessage());
                    throw new RuntimeException("Falha crítica na geração do plano.");
                }

                promptBuilder.append("\n\nERRO NA TENTATIVA ANTERIOR: ").append(e.getMessage())
                        .append("\nCORRIGE ISTO NA PRÓXIMA RESPOSTA: usa APENAS os nomes exatos do DICIONÁRIO OFICIAL fornecido, carácter a carácter, nunca repitas um exercício já usado neste plano ou no plano anterior do aluno, e escolhe aquecimento/arrefecimento coerentes com o grupo muscular do dia.");
            }
        }

        throw new RuntimeException("Falha na geração.");
    }

    /**
     * Deteta o(s) grupo(s) muscular(es) do dia a partir do texto completo do "day"
     * (ex: "Dia 5 - PULL: Costas e Bíceps" -> {COSTAS, BRAÇOS}).
     */
    private Set<String> extrairGruposMuscularesDoDia(String dayLabel) {
        if (dayLabel == null) return Set.of();
        String lower = dayLabel.toLowerCase();
        Set<String> grupos = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : GRUPO_MUSCULAR_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    grupos.add(entry.getKey());
                    break;
                }
            }
        }
        return grupos;
    }

    /**
     * Verifica se um exercício de mobilidade/alongamento é adequado ao(s) grupo(s) do dia.
     * Exercícios não mapeados (gerais) são sempre aceites. Se não foi possível determinar
     * o grupo do dia, também aceita (evita falsos positivos).
     */
    private boolean grupoMuscularCorresponde(String exerciseName, Set<String> gruposDoDia) {
        Set<String> gruposAlvo = GRUPO_ALVO_EXERCICIO_MOBILIDADE.get(exerciseName);
        if (gruposAlvo == null) return true; // exercício genérico, serve para qualquer dia
        if (gruposDoDia.isEmpty()) return true; // não foi possível determinar o grupo do dia
        return !Collections.disjoint(gruposAlvo, gruposDoDia);
    }

    /**
     * Verifica se existe, na lista de opções da categoria (aquecimento ou arrefecimento),
     * pelo menos um exercício ESPECIFICAMENTE adequado ao(s) grupo(s) do dia — usado para
     * só exigir correção quando realmente existir alternativa melhor no dicionário.
     */
    private boolean existeOpcaoMelhorParaGrupo(Set<String> gruposDoDia, List<String> opcoesDaCategoria,
                                               String exercicioEscolhido, String pathologyText) {
        if (gruposDoDia.isEmpty()) return false;
        return opcoesDaCategoria.stream()
                .filter(nome -> !nome.equalsIgnoreCase(exercicioEscolhido))
                .filter(nome -> {
                    // Exclui opções que exigem uma patologia que o aluno não tem
                    String palavraChave = MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA.get(nome);
                    if (palavraChave == null) return true; // não é pathology-gated, pode ser sugerida
                    return pathologyText != null && pathologyText.toLowerCase().contains(palavraChave);
                })
                .anyMatch(nome -> {
                    Set<String> gruposAlvo = GRUPO_ALVO_EXERCICIO_MOBILIDADE.get(nome);
                    return gruposAlvo != null && !Collections.disjoint(gruposAlvo, gruposDoDia);
                });
    }

    private String extrairCategoriaDia(String dayLabel) {
        if (dayLabel == null) return null;
        int idxTraco = dayLabel.indexOf('-');
        int idxDoisPontos = dayLabel.indexOf(':');
        if (idxTraco == -1 || idxDoisPontos == -1 || idxDoisPontos <= idxTraco) return null;
        return dayLabel.substring(idxTraco + 1, idxDoisPontos).trim();
    }

    /**
     * Normaliza, valida contra o inventário e resolve o vídeo de um exercício.
     * Lança exceção com mensagem específica se o nome não existir no dicionário —
     * essa mensagem é reaproveitada no retry para guiar a IA à correção certa.
     */
    private TrainingExercise enrichExercise(TrainingExercise ex, Set<String> validNames, String pathologyText) {
        String correctedName = normalizeExerciseName(ex.getName());

        if (!validNames.contains(correctedName)) {
            log.error("[ALERTA DE INVENTÁRIO] Nome fora do dicionário: '{}' (original da IA: '{}')",
                    correctedName, ex.getName());
            throw new RuntimeException(
                    "Exercício fora do inventário: \"" + ex.getName() + "\". " +
                            "Este nome não existe no DICIONÁRIO OFICIAL. Substitui por um nome válido da mesma categoria muscular."
            );
        }
        validarMobilidadeCondicional(correctedName, pathologyText); // NOVA VALIDAÇÃO

        String finalUrl = exerciseVideoService.getVideoUrl(correctedName);

        return TrainingExercise.builder()
                .order(ex.getOrder())
                .name(correctedName)
                .muscleGroup(ex.getMuscleGroup())
                .equipment(ex.getEquipment())
                .intensity(ex.getIntensity())
                .sets(ex.getSets())
                .reps(ex.getReps())
                .rest(ex.getRest())
                .tempo(ex.getTempo())
                .details(ex.getDetails())
                .notas(ex.getNotas())
                .weight(ex.getWeight())
                .cargaAtual(ex.getCargaAtual())
                .videoUrl(finalUrl)
                .date(java.time.LocalDate.now().toString())
                .movementPlane(ex.getMovementPlane())
                .build();
    }

    private Set<String> flattenDictionary(Map<String, List<String>> dictionary) {
        return dictionary.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        return (firstBrace != -1 && lastBrace != -1) ? cleaned.substring(firstBrace, lastBrace + 1) : cleaned;
    }

    private String calcularDescansoCientifico(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        double peso;
        try {
            peso = Double.parseDouble(cargaAtual.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            peso = 0;
        }
        if (objective.equalsIgnoreCase("Força")) return (peso > 80) ? "180" : "120";
        if (objective.equalsIgnoreCase("Hipertrofia")) return (peso > 50) ? "90" : "60";
        return "60";
    }

    private int extrairMinutosTotais(String durationText) {
        if (durationText == null || durationText.isBlank()) return 60;
        try {
            String clean = durationText.toLowerCase().trim();
            if (clean.contains("h") || clean.contains(":")) {
                String[] parts = clean.split("[h:]");
                int horas = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int minutos = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
                return (horas * 60) + minutos;
            }
            return Integer.parseInt(clean.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 60;
        }
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @NotNull
    private static String buildDiretrizAlimentar(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()), (int) (macros.carbs() * m.pctCarbs()), (int) (macros.fats() * m.pctFats())
            ));
        }
        return "DIRETRIZES ALIMENTARES:\n" + dietTable + "\nEscolha ingredientes que somem estes totais.";
    }

    /**
     * Gera a sequência de divisões de treino (splits) para a frequência semanal,
     * garantindo que nunca há o mesmo grupo muscular em dias consecutivos.
     */
    private List<String> gerarSequenciaDivisao(int frequencia, boolean isGluteFocus) {
        List<String> sequencia = new ArrayList<>();

        if (frequencia <= 1) {
            sequencia.add("FULL BODY: Corpo Inteiro");
            return sequencia;
        }

        if (frequencia == 2) {
            String[] ciclo = {
                    "SUPERIORES: Peito, Costas, Ombros e Braços",
                    "INFERIORES: Pernas e Glúteos"
            };
            for (int i = 0; i < frequencia; i++) sequencia.add(ciclo[i % ciclo.length]);
            return sequencia;
        }

        // Ciclo ordenado para que o primeiro e o último elemento nunca coincidam
        // na mesma categoria quando o ciclo dá a volta (importante para 4, 5, 6... dias)
        List<String> ciclo = isGluteFocus
                ? List.of(
                "LEGS: Glúteos e Posterior de Coxa",
                "PUSH: Peito, Ombros e Tríceps",
                "LEGS: Quadríceps e Glúteos",
                "PULL: Costas e Bíceps"
        )
                : List.of(
                "PUSH: Peito, Ombros e Tríceps",
                "PULL: Costas e Bíceps",
                "LEGS: Quadríceps, Posterior e Glúteos"
        );

        for (int i = 0; i < frequencia; i++) {
            sequencia.add(ciclo.get(i % ciclo.size()));
        }
        return sequencia;
    }

    @NotNull
    private String buildDiretrizTreino(UserProfileRequest userRequest, String durationText, int volumeIdeal) {
        boolean isGluteFocus = userRequest.getObjective() != null &&
                (userRequest.getObjective().toLowerCase().contains("glúteo") ||
                        userRequest.getObjective().toLowerCase().contains("gluteo"));

        List<String> sequencia = gerarSequenciaDivisao(userRequest.getFrequencyPerWeek(), isGluteFocus);
        String divisao = String.join(" -> ", sequencia);

        return "REGRAS DE DIVISÃO: " + divisao + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    @NotNull
    private static String buildDiretrizReabilitacao(String pathologyText) {
        StringBuilder correcao = new StringBuilder();
        String lower = pathologyText.toLowerCase();
        if (lower.contains("tornozelo")) correcao.append("- Mobilidade de Tornozelo, Propriocepção.\n");
        if (lower.contains("joelho")) correcao.append("- Ativação de Vasto Medial, Clamshell.\n");
        if (lower.contains("lombar")) correcao.append("- Deadbug, Cat-Cow, Bird-Dog.\n");
        if (lower.contains("ombro")) correcao.append("- Rotação Externa, Y-Raise.\n");
        return "REABILITAÇÃO ANATÓMICA:\n" + correcao;
    }

    // --- Sinónimos: normaliza variações comuns da IA para o nome oficial ---
    private static final Map<String, String> EXERCISE_MAP;

    static {
        Map<String, String> map = new java.util.HashMap<>();

        map.put("agachamento cálice", "Agachamento Goblet");
        map.put("agachamento goblet", "Agachamento Goblet");
        map.put("puxada corda", "Pulldown Corda");
        map.put("pulldown corda", "Pulldown Corda");
        map.put("ywt", "Y-W-T");
        map.put("y-w-t", "Y-W-T");
        map.put("mobilidade tornozelo", "Knee-to-Wall");
        map.put("tornozelo", "Knee-to-Wall");
        map.put("equilíbrio unipodal", "Equilíbrio Unipodal");
        map.put("prancha", "Prancha Abdominal");
        map.put("prancha abdominal", "Prancha Abdominal");
        map.put("cat cow", "Cat Cow");
        map.put("cat-cow", "Cat Cow");
        map.put("bird dog", "Bird Dog");
        map.put("bird-dog", "Bird Dog");
        map.put("dead bug", "Dead Bug");
        map.put("deadbug", "Dead Bug");

        // Sinónimos para os nomes canónicos únicos escolhidos no FALLBACK_DICTIONARY
        map.put("dips", "Dips");
        map.put("paralelas", "Dips");
        map.put("Dips / Paralelas", "Dips");
        map.put("stiff", "Stiff");
        map.put("rdl", "Stiff");
        map.put("Stiff / RDL", "Stiff");
        map.put("elevações", "Elevações");
        map.put("pull-ups", "Elevações");
        map.put("Elevações / Pull-ups", "Elevações");
        map.put("lunge", "Lunge");
        map.put("afundo", "Lunge");
        map.put("Lunge / Afundo", "Lunge");

        EXERCISE_MAP = Collections.unmodifiableMap(map);
    }

    // Palavras-chave para identificar o(s) grupo(s) muscular(es) do dia a partir do texto "day" (ex: "Dia 3 - PULL: Costas e Bíceps")
    private static final Map<String, Set<String>> GRUPO_MUSCULAR_KEYWORDS = Map.of(
            "PEITO", Set.of("peito", "supino", "crossover", "peck", "flex"),
            "COSTAS", Set.of("costas", "dorsal", "remada", "puxada", "pull"),
            "PERNAS", Set.of("pernas", "perna", "quadríceps", "quadriceps", "isquio", "gluteo", "glúteo", "agachamento", "leg", "coxa", "anca", "tornozelo", "gémeo", "gemeo"),
            "OMBROS", Set.of("ombro", "deltoide", "lateral", "desenvolvimento", "arnold"),
            "BRAÇOS", Set.of("braço", "braco", "tríceps", "triceps", "bíceps", "biceps", "rosca")
    );

    // Mapeia exercícios de mobilidade/alongamento específicos para o(s) grupo(s) muscular(es) que servem.
// Exercícios NÃO listados aqui são considerados GERAIS e servem para qualquer dia (ex: "Cat Cow").
    private static final Map<String, Set<String>> GRUPO_ALVO_EXERCICIO_MOBILIDADE = Map.ofEntries(
            Map.entry("Knee-to-Wall", Set.of("PERNAS")),
            Map.entry("Cossack Squat", Set.of("PERNAS")),
            Map.entry("Y-W-T", Set.of("COSTAS", "OMBROS")),
            Map.entry("Open Books", Set.of("COSTAS", "OMBROS")),
            Map.entry("Flexores da Anca", Set.of("PERNAS")),
            Map.entry("Alongamento borboleta", Set.of("PERNAS")),
            Map.entry("Alongamento do músculo grande dorsal", Set.of("COSTAS")),
            Map.entry("Walking Lunges with Reach", Set.of("PERNAS")),
            Map.entry("Frankenstein Walk", Set.of("PERNAS"))
    );

    private String normalizeExerciseName(String aiSuggestion) {
        if (aiSuggestion == null || aiSuggestion.isBlank()) return aiSuggestion;

        String cleanSuggestion = aiSuggestion.trim().replaceAll("[.,!?]$", "");
        String lowerSuggestion = cleanSuggestion.toLowerCase();

        if (EXERCISE_MAP.containsKey(lowerSuggestion)) {
            return EXERCISE_MAP.get(lowerSuggestion);
        }

        if (lowerSuggestion.contains("puxada") && lowerSuggestion.contains("frente")) {
            return "Puxada à Frente";
        }

        return cleanSuggestion;
    }

    // --- Fallback estático usado quando a BD está indisponível ou vazia ---
    private static final Map<String, List<String>> FALLBACK_DICTIONARY;

    static {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();

        map.put("PEITO", List.of(
                "Supino Plano", "Supino Inclinado", "Peck Deck", "Crossover",
                "Flexões", "Dips", "Supino com Halteres", "Aberturas com Halteres", "Flexões Diamond"
        ));
        map.put("COSTAS", List.of(
                "Puxada à Frente", "Remada Curvada", "Remada Unilateral", "Pulldown Corda",
                "Remada Baixa", "Elevações", "Puxada Pega Estreita", "Remada Cavalinho"
        ));
        map.put("PERNAS", List.of(
                "Agachamento Livre", "Leg Press 45", "Cadeira Extensora", "Mesa Flexora",
                "Stiff", "Gémeos em Pé", "Lunge", "Elevação Pélvica", "Agachamento Goblet", "Agachamento Búlgaro"
        ));
        map.put("OMBROS", List.of(
                "Desenvolvimento", "Elevação Lateral", "Face Pull", "Elevação Frontal",
                "Arnold Press", "Elevação Lateral Polia"
        ));
        map.put("BRAÇOS", List.of(
                "Rosca Direta", "Tríceps Corda", "Rosca Martelo", "Tríceps Testa", "Rosca Concentrada"
        ));
        map.put("CORE", List.of(
                "Dead Bug", "Prancha Abdominal", "Bird Dog", "Prancha Lateral", "Dead Bug com Carga"
        ));
        map.put("REAB/MOBILIDADE", List.of(
                "Cat Cow", "Clamshell", "Y-W-T", "Rotação Externa", "Knee-to-Wall", "Cossack Squat", "Equilíbrio Unipodal"
        ));
        map.put("ALONGAMENTO", List.of(
                "Alongamento Estático", "Alongamento Dinâmico", "Foam Roller", "Alongamento Isquiotibiais", "Alongamento Peitoral"
        ));

        FALLBACK_DICTIONARY = Collections.unmodifiableMap(map);
    }

    private String buildDiretrizDicionario(Map<String, List<String>> dictionary) {
        StringBuilder sb = new StringBuilder("""
                ### [REGRA DE OURO: RESTRIÇÃO RÍGIDA DE INVENTÁRIO] ###
                O sistema de vídeo FALHARÁ se usares um nome fora deste dicionário. \
                É OBRIGATÓRIO copiar o nome EXATO (incluindo acentuação e maiúsculas/minúsculas) \
                de um dos exercícios listados abaixo — nunca inventes, combines, traduzas ou abrevies nomes.
                
                [ERROS COMUNS A EVITAR — exemplos reais de falhas anteriores]
                - "Prancha" -> usa "Prancha Abdominal"
                - "Supino Reto" -> usa "Supino Plano"
                - "Alongamento" -> usa "Cat Cow", "Y-W-T" ou "Mobilidade Tornozelo"
                - "Dips / Paralelas" -> usa apenas "Dips"
                
                [REGRAS DE CORRESPONDÊNCIA EXATA]
                - Copia o nome literalmente da lista, carácter a carácter.
                - Não uses sinónimos, traduções livres, plurais ou variações estéticas do nome.
                - Se nenhum exercício da lista servir perfeitamente para o grupo muscular pretendido, escolhe o mais próximo disponível NA MESMA CATEGORIA — nunca inventes um nome novo.
                
                [DICIONÁRIO OFICIAL DE EXERCÍCIOS DISPONÍVEIS]
                """);

        dictionary.forEach((categoria, nomes) ->
                sb.append("- ").append(categoria.toUpperCase())
                        .append(": ").append(String.join(", ", nomes))
                        .append("\n")
        );

        sb.append("""
                
                Nota: Se um exercício for de mobilidade/reabilitação (categoria REAB/MOBILIDADE), \
                ele DEVE ser o primeiro exercício do treino (Order 1).
                """);

        return sb.toString();
    }

    private static final Map<String, String> MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA = Map.of(
            "Rotação Externa", "ombro",
            "Clamshell", "joelho"
    );

    private void validarMobilidadeCondicional(String exerciseName, String pathologyText) {
        String palavraChave = MOBILIDADE_ESPECIFICA_PARA_PATOLOGIA.get(exerciseName);
        if (palavraChave != null) {
            boolean patologiaCorresponde = pathologyText != null
                    && pathologyText.toLowerCase().contains(palavraChave);

            if (!patologiaCorresponde) {
                throw new RuntimeException(
                        "Exercício de mobilidade específica \"" + exerciseName +
                                "\" usado sem a patologia correspondente (\"" + palavraChave +
                                "\"). O aluno relatou: \"" + pathologyText + "\". " +
                                "Usa mobilidade GERAL em vez disso (Cat Cow, Y-W-T, Dead Bug, Bird Dog, Equilíbrio Unipodal)."
                );
            }
        }
    }
}