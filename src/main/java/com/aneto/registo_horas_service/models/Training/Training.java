package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.mapper.ExerciseVideoMapper;
import com.aneto.registo_horas_service.models.Enum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Slf4j
public record Training(ChatModel chatModel, ObjectMapper objectMapper) {

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {

        log.info(" entri no generateTrainingPlan: ");

        // 1. Sanitização de entradas
        String exerciseHistoryText = defaultIfEmpty(userRequest.exerciseHistory(), "Não informado");
        String objectiveText = defaultIfEmpty(userRequest.objective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.location(), "Não especificada");
        String countryText = defaultIfEmpty(userRequest.country(), "Não especificado");
        String bodyTypeText = (userRequest.bodyType() != null) ? userRequest.bodyType().name() : "ECTOMORPH";
        String genderText = (userRequest.gender() != null) ? userRequest.gender().name() : "MALE";
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.weightKg()), "70");

        String durationText = (userRequest.durationPerSession() != null && !userRequest.durationPerSession().isBlank())
                ? userRequest.durationPerSession() : "60 minutos";

        String protocolId = (userRequest.protocol() == null) ? "nasm_estabilizacao" : userRequest.protocol();
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        int totalMinutos = extrairMinutosTotais(durationText);
        int volumeIdeal = Math.max(6, totalMinutos / 7);
        String pathologyText = (userRequest.pathology() == null || userRequest.pathology().isBlank()) ? "Nenhuma limitação relatada" : userRequest.pathology();

        // 2. Cálculos Nutricionais Protocolo e Macros
        log.info(" Cálculos Nutricionais Protocolo e Macros");
        Macros macros = MacroCalculator.calculate(
                userRequest.weightKg(),
                userRequest.heightCm(),
                userRequest.age(),
                (userRequest.gender() != null ? userRequest.gender().name() : "MALE"), // .name() em vez de .toString()
                (userRequest.bodyType() != null ? userRequest.bodyType().name() : "MESOMORPH"),
                userRequest.bodyFat() != null ? userRequest.bodyFat() : 15.0,
                userRequest.mealsPerDay() != null ? userRequest.mealsPerDay() : 6
        );
        // --- SEPARAÇÃO DAS REGRAS (DIRETRIZES) ---
        log.info("SEPARAÇÃO DAS REGRAS (DIRETRIZES");
        String descansoCientifico = calcularDescansoCientifico(protocol, objectiveText, weightKg);


        // 1. Identificar se é um aluno "Risco Zero"
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.exerciseHistory());

        // 2. Ajustar o protocolo se for sedentário (Prevenção de mal-estar)
        String protocoloEfetivo = isSedentary ? "Adaptação Anatómica (Baixa Intensidade)" : protocol.getLabel();
        String repsEfetivas = isSedentary ? "12 a 15 (longe da falha)" : protocol.getReps();
        String setsEfetivas = isSedentary ? "2" : protocol.getSets();

        // Forçar descanso maior para iniciantes para evitar náuseas (resíntese de ATP e estabilização de pressão)
        String descansoEfetivo = isSedentary ? "120" : calcularDescansoCientifico(protocol, objectiveText, weightKg);


        // 3. Nova Diretriz de Segurança (ADICIONAR AO PROMPT)
        String diretrizSegurancaIniciante = isSedentary ? """
                [ALERTA DE SEGURANÇA: ALUNO SEDENTÁRIO]
                - O aluno nunca treinou. É TERMINANTEMENTE PROIBIDO levar à falha concêntrica.
                - Prioridade: Estabilidade hemodinâmica.
                - Não usar superséries.
                - Evitar exercícios com a cabeça abaixo do nível do coração.
                - Foco em máquinas (maior estabilidade).
                """ : "";


        String detalhesEquipamento = locationText.equalsIgnoreCase("Casa") ?
                "UTILIZA APENAS: 'Peso Corporal', 'Halteres' ou 'Bandas Elásticas'. PROIBIDO o uso de máquinas de ginásio." :
                "UTILIZA: 'Máquinas', 'Barras', 'Polias' ou 'Halteres'.";

        String filtroEquipamento = isSedentary ?
                "PREFERÊNCIA OBRIGATÓRIA: Máquinas guiadas para maior controlo motor e segurança." :
                detalhesEquipamento;

        String diretrizProtocolo = """
        DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):
        - Séries: %s | Repetições: %s
        - DESCANSO CIENTÍFICO: %s segundos fixos.
        - RITMO (Tempo): %s
        - JUSTIFICATIVA FISIOLÓGICA: O descanso de %s é calculado para garantir a resíntese de Fosfocreatina e estabilidade hemodinâmica.
        """.formatted(
                protocoloEfetivo, // %s - Nome do protocolo adaptado
                setsEfetivas,     // %s - Sérias adaptadas
                repsEfetivas,     // %s - Repetições adaptadas
                descansoEfetivo,  // %s - Descanso adaptado
                protocol.getTempo(),
                descansoEfetivo   // %s - Justificativa com o descanso correto
        );

        String diretrizBiomecanica = """
                DIRETRIZES DE SELEÇÃO BIOMECÂNICA ESTREITAS:
                1. REPERTÓRIO: %s.
                2. PROIBIDOS: %s.
                3. ADAPTAÇÃO: Patologia "%s", substitui impactos por baixo torque.
                4. SEPARAÇÃO TOTAL (ERRO CRÍTICO): É terminantemente PROIBIDO incluir exercícios de PERNAS (Ex: Agachamentos, Elevação Pélvica, Leg Press) em dias de SUPERIORES (Push/Pull).
                5. COERÊNCIA: Se o dia é PUSH, usa apenas Peito, Ombros e Tríceps. Se o dia é LEGS, usa apenas Quadríceps, Isquiotibiais e Glúteos.
                """.formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = """
                REGRA OBRIGATÓRIA DE AQUECIMENTO:
                - Se houver Reabilitação, "order": 2. Caso contrário, "order": 1.
                - Foco específico na região: %s.
                """.formatted(pathologyText);

        String diretrizTreino = getString(userRequest, pathologyText, protocol, durationText, descansoCientifico, volumeIdeal);
        String diretrizAlimentar = getString(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Podes escolher exercícios da base que respeitem o protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.frequencyPerWeek());

        String diretrizEquipamento = """
        LOGÍSTICA E EQUIPAMENTO:
        - Localização atual: %s.
        - REGRA ABSOLUTA: O campo 'equipment' no JSON deve indicar claramente o material necessário.
        - %s
        - VÍDEOS: Usa obrigatoriamente nomes do DICIONÁRIO para garantir o mapeamento.
        """.formatted(locationText, filtroEquipamento);

        String diretrizArrefecimento = """
                REGRA DE ARREFECIMENTO:
                - O ÚLTIMO exercício de cada dia deve ser Alongamento Estático focado em %s.
                - No campo "reps", especifica o tempo (ex: "30-60 seg").
                """.formatted(pathologyText);

        String diretrizIntensidade = "INTENSIDADE: Atribua BAIXA, MODERADA ou ALTA para cada exercício (campo 'intensity').";

        // 1. Define a divisão muscular exata e a reabilitação
        String diretrizNomenclaturaDias = """
                REGRAS ESTRITAS DE DIVISÃO E EXCLUSIVIDADE (FREQUÊNCIA %d):
                1. NOMENCLATURA E FOCO:
                   - "Dia 1 - PUSH: Peito/Ombros/Tríceps"
                   - "Dia 2 - PULL: Costas/Bíceps"
                   - "Dia 3 - LEGS: Pernas e Core"
                2. REABILITAÇÃO DIÁRIA: Todos os dias DEVEM começar (Order 1) com um exercício de reabilitação para %s. Escolha um diferente para cada dia.
                3. PROIBIÇÃO BIOMECÂNICA: É terminantemente PROIBIDO incluir exercícios de pernas ou glúteos (ex: Agachamentos, Elevação Pélvica, Leg Press) nos dias de PUSH ou PULL.
                4. VARIABILIDADE: Cada bloco de treino deve ter exercícios 100%% únicos. Não repitas o treino do Dia 1 no Dia 3.
                5. QUANTIDADE: Gera exatamente %d blocos no array "plan".
                """.formatted(userRequest.frequencyPerWeek(), pathologyText, userRequest.frequencyPerWeek());

        String diretrizReabilitacao = pathologyText.contains("Nenhuma") ?
                "Foca o primeiro exercício em mobilidade geral ou ativação dinâmica." :
                getString(pathologyText) + "\n- REGRA: Proibido repetir o mesmo exercício de reabilitação em dias consecutivos.";

        String regrasFinais = """
        REGRAS CRÍTICAS DE FECHAMENTO:
        1. DURAÇÃO E VOLUME: O treino deve durar %d minutos. Para isso, gera EXATAMENTE %d exercícios por dia.
        2. RITMO E DESCANSO: Usa obrigatoriamente Ritmo %s e Descanso %s segundos.
        3. FORMATO: Responde APENAS o JSON puro. Proibido usar hífens em números.
        4. FREQUÊNCIA: O JSON deve conter exatamente %d dias no array "plan".
        5. TOTAIS DIETA: %d kcal, %dg Prot, %dg Carbs, %dg Fats.
        """.formatted(
                totalMinutos,
                volumeIdeal,
                protocol.getTempo(),
                descansoEfetivo, // <--- Agora usa o descanso adaptado (120 se sedentário)
                userRequest.frequencyPerWeek(),
                macros.dailyCalories(),
                macros.protein(),
                macros.carbs(),
                macros.fats()
        );
        String diretrizDicionario = """
                DICIONÁRIO DE NOMES OBRIGATÓRIOS (Usa APENAS estes nomes exatos para garantir o vídeo):
                - Peito: Supino Plano, Supino Inclinado, Peck Deck, Crossover.
                - Costas: Puxada à Frente, Remada Curvada, Remada Unilateral, Pulldown Corda.
                - Pernas: Agachamento Livre, Leg Press 45, Cadeira Extensora, Mesa Flexora, Stiff / RDL, Gémeos em Pé, Mobilidade Tornozelo.
                - Ombros/Braços: Desenvolvimento, Elevação Lateral, Face Pull, Rosca Direta, Tríceps Corda.
                - Reabilitação: Dead Bug, Cat Cow, Clamshell, Y-W-T, Rotação Externa.
                """;

        String diretrizPlanosMovimento = """
                VARIABILIDADE BIOMECÂNICA (Planos de Movimento):
                - REGRA: No mesmo dia, deves incluir exercícios que explorem diferentes planos:
                  1. PLANO SAGITAL: Movimentos de flexão/extensão (ex: Agachamento, Supino, Remada).
                  2. PLANO FRONTAL: Movimentos de abdução/adução (ex: Elevação Lateral, Puxada à Frente, Abdução de anca).
                  3. PLANO TRANSVERSAL: Movimentos de rotação (ex: Rotação Externa, Woodchopper, Prancha Lateral com rotação).
                - IMPORTANTE: Garante que a Reabilitação e o Core explorem os planos Transversal ou Frontal para compensar os exercícios de força que são maioritariamente Sagitais.
                """;

        String diretrizAnatomiaDetalhada = """
                DETALHAMENTO ANATÓMICO EXAUSTIVO:
                - No campo 'muscleGroup', deves listar TODOS os músculos envolvidos no movimento, divididos por:
                  1. Motores Primários (Agonistas).
                  2. Sinergistas (Músculos que auxiliam).
                  3. Estabilizadores (Fixadores).
                - Exemplo para Supino: "Peitoral Maior (Fibras Médias e Inferiores), Deltoide Anterior, Tríceps Braquial, Serrátil Anterior e Coracobraquial".
                - Exemplo para Agachamento: "Quadríceps (Vasto Lateral, Medial, Intermédio e Reto Femoral), Glúteo Maior, Isquiotibiais, Eretores da Espinha e Transverso do Abdómen".
                """;

        String diretrizAlimentarDetalhada = """
                DIRETRIZES NUTRICIONAIS (FUNÇÃO METABÓLICA):
                - O plano atual tem um total de %d kcal.
                - Explica no 'summary' como os macros vão recuperar os músculos listados:
                - Proteína: Reparação das fibras dos motores primários (agonistas).
                - Carbohidratos: Reposição de glicogénio para o próximo treino.
                - Gorduras: Suporte hormonal para a síntese proteica.
                """.formatted(macros.dailyCalories());

        String diretrizInstrucoesDetalhadas = """
                REGRAS DE FEEDBACK TÉCNICO:
                - No campo 'details': Explica a execução técnica focando no Plano de Movimento. 
                  (Ex: "Mantém os cotovelos a 45º do corpo para proteger o manguito rotador no plano sagital").
                - No campo 'notas': Indica a 'Pista Mental' para máxima ativação muscular.
                  (Ex: "Imagina que queres esmagar uma noz entre as tuas escápulas durante a remada").
                """;


        String diretrizCargasDinamicas = """
                SISTEMA DINÂMICO DE CARGAS (RPE):
                - Analisa o histórico do aluno: "%s".
                - REGRA DE OURO: O campo 'cargaAtual' NÃO pode ser estático.
                - Se o nível for 'sedentary' ou 'return_long':RPE 4-5 (Esforço muito leve, foco total na respiração e técnica).
                - Se o nível for 'beginner': Sugere RPE 6-7 (Moderado).
                - Se o nível for 'intermediate': Sugere RPE 7-8 (Intenso).
                - Se o nível for 'advanced', 'elite' ou 'athlete': Sugere RPE 9 (Alta intensidade).
                - No campo 'cargaAtual', escreve a orientação e a justificativa (ex: "RPE 8 - Carga desafiante para 2 reps em reserva").
                """.formatted(exerciseHistoryText);

        // --- JUNÇÃO DE TODAS AS REGRAS (Fisiologia, Biomecânica e Nutrição) ---
        String blocoDiretrizesCompletas = """
                [PERFIL E HISTÓRICO]
                - Experiência Anterior: %s
                - Localização do Aluno: %s
                
                [REABILITAÇÃO E FISIOLOGIA]
                %s
                %s
                %s
                %s
                
                [BIOMECÂNICA, ANATOMIA E INSTRUÇÕES]
                %s
                %s
                %s
                %s
                %s
                
                [LOGÍSTICA E EQUIPAMENTO]
                %s
                %s
                
                [ESTRUTURA E REPERTÓRIO]
                %s
                %s
                %s
                
                [ESTRUTURA DOS DIAS E NOMENCLATURA]
                %s
                [CONTROLO DE NOMENCLATURA]
                %s
                
                [DIRETRIZES ALIMENTARES E METABÓLICAS]
                %s
                %s
                """.formatted(
                // 1 & 2: Perfil
                exerciseHistoryText, diretrizSegurancaIniciante,countryText,
                // 3, 4 & 5: Fisiologia
                diretrizProtocolo, diretrizReabilitacao, diretrizAquecimento,
                // 6, 7, 8 & 9: Biomecânica (Adicionei o placeholder que faltava para a diretrizInstrucoesDetalhadas)
                diretrizBiomecanica, diretrizPlanosMovimento, diretrizAnatomiaDetalhada, diretrizInstrucoesDetalhadas, diretrizCargasDinamicas,
                // 10 & 11: Logística
                diretrizEquipamento, diretrizIntensidade,
                // 12, 13 & 14: Estrutura
                diretrizTreino, diretrizRepertorio, diretrizArrefecimento,
                // 15 & 16: Nomenclatura
                diretrizNomenclaturaDias, diretrizDicionario,
                // 17 & 18: Alimentar
                diretrizAlimentar, diretrizAlimentarDetalhada
        );
        String userPrompt = """
                ATUAÇÃO: Personal Trainer e Nutricionista Profissional (Portugal).
                FOCO: Reabilitação e Performance Fisiológica.
                
                PERFIL DO ALUNO:
                - %d anos, %s, %s, %.2fkg. Objetivo: %s.
                - Patologias/Lesões: %s
                
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
                        "muscleGroup": "LISTAGEM COMPLETA: Agonistas, Sinergistas e Estabilizadores (ex: Peitoral Maior, Deltoide Anterior, Tríceps, Serrátil)",
                        "movementPlane": "Sagital / Frontal / Transversal",
                        "equipment": "Halteres / Máquina / Peso Corporal",
                        "tempo": "%s", "sets": "3", "reps": "15", "rest": "%s",
                        "weight": "0kg",
                            "cargaAtual": "Adaptar conforme RPE",
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
                    "meals": [
                    {
                    "time": "HH:mm",
                    "description": "Nome da Refeição",
                    "ingredients": ["..."],
                    "calories": 0,
                    "protein": 0,
                    "carbs": 0,
                    "fats": 0
                    }
                    ]
                    }
                }
                """.formatted(
                // Aluno (6 placeholders)
                userRequest.age(), bodyTypeText, genderText, userRequest.weightKg(), objectiveText, pathologyText,
                // Regras Agrupadas (2 placeholders: blocoDiretrizes e regrasFinais)
                blocoDiretrizesCompletas, regrasFinais,
                // Template JSON (10 placeholders)
                protocol.getLabel(), protocol.getTempo(), descansoCientifico,
                macros.dailyCalories(), macros.imc(), macros.imcCategory(),
                macros.protein(), macros.carbs(), macros.fats()
        );
        return executeGeneration(userPrompt, userRequest, totalMinutos);
    }

    @NotNull
    private static String getString(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()), (int) (macros.carbs() * m.pctCarbs()), (int) (macros.fats() * m.pctFats())
            ));
        }

        return """
                DIRETRIZES ALIMENTARES ESTRITAS:
                1. O plano deve conter estas %d refeições com estes valores:
                %s
                2. REGRA: Escolha ingredientes que somem exatamente estes totais: %d kcal, %dg P, %dg C, %dg G.
                """.formatted(macros.mealSuggestions().size(), dietTable.toString(), (int) macros.dailyCalories(), (int) macros.protein(), (int) macros.carbs(), (int) macros.fats());
    }

    @NotNull
    private static String getString(UserProfileRequest userRequest, String pathologyText, Enum.TrainingProtocol protocol, String durationText, String descanso, int volumeIdeal) {
        String divisao = switch (userRequest.frequencyPerWeek()) {
            case 1 -> "FULL BODY: Foco em grandes grupos musculares.";
            case 2 -> "SUPERIOR (Peito/Costas/Braços) e INFERIOR (Pernas/Core).";
            case 3 ->
                    "ESTRUTURA OBRIGATÓRIA: Dia 1 - PUSH (Peito, Tríceps e Ombros); Dia 2 - PULL (Costas e Bíceps); Dia 3 - LEGS (Pernas e Core).";
            default -> "Divisão customizada por grupos isolados.";
        };

        return """
                REGRAS CRÍTICAS DE DIVISÃO E FISIOLOGIA:
                1. COERÊNCIA ANATÓMICA: No Dia 1 (PUSH), é PROIBIDO incluir pernas/glúteos. Estes pertencem ao Dia 3.
                2. DIVISÃO DO PLANO: %s
                3. DURAÇÃO E VOLUME: O treino dura %s. Gere EXATAMENTE %d exercícios por dia.
                4. REABILITAÇÃO: O "Order 1" de cada dia deve ser um exercício para %s.
                5. PARÂMETROS DINÂMICOS (PROTOCOLO %s):
                   - SÉRIES: "%s"
                   - REPETIÇÕES: "%s"
                   - DESCANSO: "%s" segundos
                   - RITMO (Tempo): "%s"
                """.formatted(
                divisao, durationText, volumeIdeal,
                pathologyText,
                protocol.getLabel(),      // Nome do protocolo (ex: Hipertrofia)
                protocol.getSets(),       // Puxa as séries do Enum
                protocol.getReps(),       // Puxa as reps do Enum
                descanso,
                protocol.getTempo()       // Puxa o ritmo (ex: 4-2-1 ou 2-0-2)
        );
    }

    @NotNull
    private static String getString(String pathologyText) {
        StringBuilder correcao = new StringBuilder();
        String lower = pathologyText.toLowerCase();

        if (lower.contains("tornozelo")) {
            correcao.append("- OPÇÕES DE REABILITAÇÃO: Mobilidade de Tornozelo com Elástico, Propriocepção Unipodal, Tibial Anterior no Smith ou Liberação Miofascial da Fáscia Plantar.\n");
        }
        if (lower.contains("joelho")) {
            correcao.append("- OPÇÕES DE REABILITAÇÃO: Ativação de Vasto Medial (VMO), Clamshell (Ostra) para Glúteo Médio, ou Step-down controlado.\n");
        }
        if (lower.contains("lombar")) {
            correcao.append("- OPÇÕES DE REABILITAÇÃO: Deadbug para Bracing, Cat-Cow para mobilidade vertebral, ou Bird-Dog (Perdigueiro).\n");
        }
        if (lower.contains("ombro")) {
            correcao.append("- OPÇÕES DE REABILITAÇÃO: Rotação Externa com Elástico, Y-Raise para Trapézio Inferior, ou Protusão de Escápula.\n");
        }

        return """
                REGRAS ANATÓMICAS DE REABILITAÇÃO:
                %s
                1. VARIABILIDADE: Escolhe um exercício DIFERENTE da lista acima para o "Order 1" de cada dia de treino.
                2. PROGRESSÃO: O foco deve ser técnico e de baixa intensidade (ativação).
                """.formatted(correcao.toString());
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private TrainingPlanResponse executeGeneration(String prompt, UserProfileRequest userRequest, int totalMinutos) {
        log.info(" entrei no executeGeneration :{}", LocalDateTime.now(ZoneId.of("UTC")).toString());
        int maxRetries = 2;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String textResponse = chatModel.call(prompt);
                String cleanedJson = cleanMarkdown(textResponse);

                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // VALIDAÇÃO DE VOLUME
                int minExAceitavel = Math.max(5, (totalMinutos / 8));
                boolean isVolumeValido = response.getPlan() != null && !response.getPlan().isEmpty() &&
                        response.getPlan().stream().allMatch(d -> d.exercises() != null && d.exercises().size() >= minExAceitavel);

                if (!isVolumeValido) {
                    throw new RuntimeException("Volume insuficiente para " + totalMinutos + " min.");
                }

                // MAPEAMENTO E ENRIQUECIMENTO DE VÍDEOS E DADOS
                List<TrainingDay> updatedPlan = response.getPlan().stream()
                        .map(day -> {
                            List<TrainingExercise> enrichedExercises = day.exercises().stream()
                                    .map(ex -> {
                                        // 1. Mapeamento do Vídeo utilizando o Mapper que criámos
                                        // Se o EnumExerciseCategory for a classe onde colocaste o Map:
                                        String finalUrl = ExerciseVideoMapper.getVideoUrl(ex.name());

                                        // 2. Data de hoje formatada
                                        String today = java.time.LocalDate.now().toString();

                                        // 3. Retorna o Record TrainingExercise com os campos alinhados ao TypeScript
                                        return new TrainingExercise(
                                                ex.order(),
                                                ex.name(),
                                                ex.muscleGroup(),
                                                ex.equipment(),
                                                ex.intensity(),
                                                ex.sets(),
                                                ex.reps(),
                                                ex.rest(),
                                                ex.tempo(),
                                                ex.details(),
                                                ex.notas(),
                                                ex.weight(),      // Carga sugerida (weight)
                                                ex.cargaAtual(),  // Orientação RPE
                                                finalUrl,         // URL mapeada automaticamente
                                                today,            // Data da geração
                                                ex.movementPlane()
                                        );
                                    }).toList();
                            return new TrainingDay(day.day(), enrichedExercises);
                        }).toList();

                // Retorno final com o perfil do utilizador para manter o estado no Frontend
                return new TrainingPlanResponse(false, response.getSummary(), updatedPlan, response.getDietPlan(), userRequest);

            } catch (Exception e) {
                log.error("Erro na tentativa {}: {}", attempt, e.getMessage());
                if (attempt >= maxRetries) {
                    throw new RuntimeException("Erro após " + (maxRetries + 1) + " tentativas: " + e.getMessage());
                }
                // Reforço do prompt para a próxima tentativa
                int volumeSugestao = Math.max(6, totalMinutos / 7);
                prompt += "\nERRO TÉCNICO: O JSON falhou. Garante que geras exatamente " + volumeSugestao +
                        " exercícios e usa apenas nomes do dicionário: Supino Plano, Leg Press 45, etc.";
            }
        }
        throw new RuntimeException("Falha crítica na geração.");
    }

    private String cleanMarkdown(String text) {
        log.info(" entrei no calcularDescansoCientifico :{}", LocalDateTime.now(ZoneId.of("UTC")).toString());
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        return (firstBrace != -1 && lastBrace != -1) ? cleaned.substring(firstBrace, lastBrace + 1) : cleaned;
    }

    private String calcularDescansoCientifico(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        log.info(" entrei no calcularDescansoCientifico");
        double peso = 0;
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
        log.info(" entrei no extrairMinutosTotais");
        if (durationText == null || durationText.isBlank()) return 60;
        try {
            String clean = durationText.toLowerCase().trim();
            if (clean.contains("h") || clean.contains(":")) {
                String[] parts = clean.split("[h:]");
                int horas = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int minutos = (parts.length > 1 && !parts[1].isEmpty())
                        ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
                return (horas * 60) + minutos;
            }
            return Integer.parseInt(clean.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 60;
        }
    }
}