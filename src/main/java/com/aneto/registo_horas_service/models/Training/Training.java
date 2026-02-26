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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        String countryText = defaultIfEmpty(userRequest.getCountry(), "Não especificado");
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

        // --- NOVIDADE: DETEÇÃO DE FOCO ESPECÍFICO (EX: GLÚTEO) ---
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
                - Se houver patologia ("%s"), o exercício de "order": 1 DEVE ser de Reabilitação/Mobilidade focado nessa área.
                - Se não houver patologia, o "order": 1 deve ser Mobilidade Geral (ex: Cat Cow ou Y-W-T).
                - O aquecimento prepara a articulação para o esforço subsequente.
                """.formatted(pathologyText);

        String diretrizTreino = getString(userRequest, pathologyText, protocol, durationText, descansoEfetivo, volumeIdeal);
        String diretrizAlimentar = getString(macros);

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
                - O ÚLTIMO exercício de cada dia deve ser Alongamento Estático focado em %s.
                - No campo "reps", especifica o tempo (ex: "30-60 seg").
                """.formatted(pathologyText);

        String nomenclaturaBase = isGluteFocus ? "Foco Glúteos/Inferiores" : "PUSH/PULL/LEGS (Foco Hipertrofia)";

        String diretrizNomenclaturaDias = """
        REGRAS ESTRITAS DE DIVISÃO (FREQUÊNCIA %d DIAS):
        1. NOMENCLATURA: O campo "name" de cada bloco deve seguir o padrão: "Dia X - [FOCO]: [GRUPOS MUSCULARES]". 
           - Use a base: %s.
        2. REABILITAÇÃO DIÁRIA (OBRIGATÓRIO): O primeiro exercício (Order 1) de TODOS os dias deve ser obrigatoriamente para %s.
        3. VARIABILIDADE: Proibido repetir exercícios entre os dias. Cada bloco deve ter 100%% de exercícios únicos.
        4. ESTRUTURA: Gere exatamente %d blocos dentro do array "plan".
        """.formatted(userRequest.getFrequencyPerWeek(), nomenclaturaBase, pathologyText, userRequest.getFrequencyPerWeek()
        );

        String diretrizReabilitacao = pathologyText.contains("Nenhuma") ?
                "Foca o primeiro exercício em mobilidade geral ou ativação dinâmica." :
                getString(pathologyText) + "\n- REGRA: Proibido repetir o mesmo exercício de reabilitação em dias consecutivos.";

        String diretrizDicionario = """
                ### [REGRA DE OURO: RESTRIÇÃO RÍGIDA DE INVENTÁRIO] ###
                O sistema de vídeo falhará se usares nomes genéricos. É OBRIGATÓRIO usar os nomes exatos do dicionário abaixo.
                
                [PROIBIÇÕES CRÍTICAS]
                - NUNCA uses "Prancha" -> Usa "Prancha Abdominal".
                - NUNCA uses "Alongamento" -> Usa "Cat Cow", "Y-W-T" ou "Mobilidade Tornozelo".
                - NUNCA inventes variações como "Supino Reto" -> Usa "Supino Plano".
                
                [DICIONÁRIO OFICIAL SQL]
                - PEITO: Supino Plano, Supino Inclinado, Peck Deck, Crossover, Flexões, Dips / Paralelas, Supino com Halteres, Aberturas com Halteres, Flexões Diamond
                - COSTAS: Puxada à Frente, Remada Curvada, Remada Unilateral, Pulldown Corda, Remada Baixa, Elevações / Pull-ups, Puxada Pega Estreita, Remada Cavalinho
                - PERNAS: Agachamento Livre, Leg Press 45, Cadeira Extensora, Mesa Flexora, Stiff / RDL, Gémeos em Pé, Lunge / Afundo, Elevação Pélvica, Agachamento Goblet, Agachamento Búlgaro
                - OMBROS: Desenvolvimento, Elevação Lateral, Face Pull, Elevação Frontal, Arnold Press, Elevação Lateral Polia
                - BRAÇOS: Rosca Direta, Tríceps Corda, Rosca Martelo, Tríceps Testa, Rosca Concentrada
                - CORE: Dead Bug, Prancha Abdominal, Bird Dog, Prancha Lateral, Dead Bug com Carga
                - REAB/MOBILIDADE: Cat Cow, Clamshell, Y-W-T, Rotação Externa, Mobilidade Tornozelo, Equilíbrio Unipodal
                
                Nota: Se um exercício for de mobilidade/reabilitação, ele DEVE ser o primeiro do treino (Order 1).
                """;
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

        // --- CONSTRUÇÃO DO PROMPT FINAL ---
        String blocoDiretrizesCompletas = String.join("\n", diretrizFocoEspecial,
                diretrizVariedade,diretrizAquecimento, diretrizSegurancaIniciante, diretrizProtocolo, diretrizReabilitacao,
                diretrizBiomecanica, diretrizAnatomiaDetalhada, diretrizCargasDinamicas,
                diretrizEquipamento, diretrizTreino, diretrizRepertorio, diretrizArrefecimento,
                diretrizNomenclaturaDias, diretrizDicionario, diretrizAlimentar
        );

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

        return executeGeneration(userPrompt, userRequest, totalMinutos);
    }

// ... (mantenha os imports e o início da classe iguais) ...

    private TrainingPlanResponse executeGeneration(String prompt, UserProfileRequest userRequest, int totalMinutos) {
        log.info("Iniciando executeGeneration no ChatModel.");
        int maxRetries = 5;

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

                // 3. MAPEAMENTO DE EXERCÍCIOS (FORÇANDO ARRAYLIST)
                int finalAttempt = attempt;
                List<TrainingDay> updatedPlan = new ArrayList<>(); // Criamos uma lista mutável explicitamente

                if (response.getPlan() != null) {
                    for (TrainingDay day : response.getPlan()) {
                        List<TrainingExercise> enrichedExercises = day.getExercises().stream()
                                .map(ex -> {
                                    String correctedName = normalizeExerciseName(ex.getName());
                                    String finalUrl = exerciseVideoService.getVideoUrl(correctedName);

                                    if (finalUrl.contains("youtube.com/results")) {
                                        log.error("[ALERTA DE INVENTÁRIO] A IA usou '{}'.", ex.getName());
                                        throw new RuntimeException("Exercício inválido detectado: " + ex.getName());
                                    }

                                    // Usamos o Builder da CLASSE (não Record)
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
                                })
                                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

                        updatedPlan.add(new TrainingDay(day.getDay(), enrichedExercises));
                    }
                }

                // 4. LIMPEZA DA DIETA (PONTO ONDE O ERRO PODE ESTAR)
                DietPlan diet = response.getDietPlan();
                if (diet != null) {
                    // Garante que a lista de refeições é mutável
                    if (diet.getMeals() != null) {
                        List<Meal> mutableMeals = new ArrayList<>();
                        for (Meal m : diet.getMeals()) {
                            // Garante que os ingredientes dentro de cada refeição também sejam mutáveis
                            if (m.getIngredients() != null) {
                                m.setIngredients(new ArrayList<>(m.getIngredients()));
                            }
                            mutableMeals.add(m);
                        }
                        diet.setMeals(mutableMeals);
                    }
                }

                log.info("Geração concluída com sucesso e validada contra inventário.");

                // 5. RESPOSTA FINAL (Uso de Builder para garantir objeto limpo)
                return TrainingPlanResponse.builder()
                        .isExistingPlan(false)
                        .summary(response.getSummary())
                        .plan(updatedPlan)
                        .dietPlan(diet)
                        .userProfile(null) // UserProfileRequest agora é uma Classe DTO
                        .build();

            } catch (Exception e) {
                log.warn("Falha na tentativa {}/{} - Erro: {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("Todas as tentativas falharam. Erro final: {}", e.getMessage());
                    throw new RuntimeException("Falha crítica na geração do plano.");
                }

                promptBuilder.append("\n\nERRO NA TENTATIVA ANTERIOR: ").append(e.getMessage()).append("\nPOR FAVOR, USA APENAS OS NOMES EXATOS DO DICIONÁRIO FORNECIDO.");
            }
        }
        prompt = promptBuilder.toString();
        throw new RuntimeException("Falha na geração.");
    }
    // ... (mantenha o resto dos métodos auxiliares iguais) ...
    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        return (firstBrace != -1 && lastBrace != -1) ? cleaned.substring(firstBrace, lastBrace + 1) : cleaned;
    }

    private String calcularDescansoCientifico(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
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
    private static String getString(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) {
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(), m.time(), (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()), (int) (macros.carbs() * m.pctCarbs()), (int) (macros.fats() * m.pctFats())
            ));
        }
        return "DIRETRIZES ALIMENTARES:\n" + dietTable + "\nEscolha ingredientes que somem estes totais.";
    }

    @NotNull
    private static String getString(UserProfileRequest userRequest, String pathologyText, Enum.TrainingProtocol protocol, String durationText, String descanso, int volumeIdeal) {
        String divisao = switch (userRequest.getFrequencyPerWeek()) {
            case 1 -> "FULL BODY";
            case 2 -> "SUPERIOR / INFERIOR";
            case 3 -> "PUSH / PULL / LEGS";
            default -> "Divisão customizada";
        };
        return "REGRAS DE DIVISÃO: " + divisao + " | Duração: " + durationText + " | Volume: " + volumeIdeal + " ex/dia.";
    }

    @NotNull
    private static String getString(String pathologyText) {
        StringBuilder correcao = new StringBuilder();
        String lower = pathologyText.toLowerCase();
        if (lower.contains("tornozelo")) correcao.append("- Mobilidade de Tornozelo, Propriocepção.\n");
        if (lower.contains("joelho")) correcao.append("- Ativação de Vasto Medial, Clamshell.\n");
        if (lower.contains("lombar")) correcao.append("- Deadbug, Cat-Cow, Bird-Dog.\n");
        if (lower.contains("ombro")) correcao.append("- Rotação Externa, Y-Raise.\n");
        return "REABILITAÇÃO ANATÓMICA:\n" + correcao;
    }

    private static final Map<String, String> EXERCISE_MAP;

    static {
        Map<String, String> map = new java.util.HashMap<>();

        // Mapeamento de Sinónimos (Chave minúscula -> Nome oficial no Inventário)
        map.put("agachamento cálice", "Agachamento Goblet");
        map.put("agachamento goblet", "Agachamento Goblet");
        map.put("puxada corda", "Pulldown Corda");
        map.put("pulldown corda", "Pulldown Corda");
        map.put("ywt", "Y-W-T");
        map.put("y-w-t", "Y-W-T");
        map.put("mobilidade tornozelo", "Mobilidade Tornozelo");
        map.put("equilíbrio unipodal", "Equilíbrio Unipodal");
        map.put("prancha", "Prancha Abdominal");
        map.put("prancha abdominal", "Prancha Abdominal");
        map.put("cat cow", "Cat Cow");
        map.put("cat-cow", "Cat Cow");
        map.put("bird dog", "Bird Dog");
        map.put("bird-dog", "Bird Dog");
        map.put("dead bug", "Dead Bug");
        map.put("deadbug", "Dead Bug");

        EXERCISE_MAP = Collections.unmodifiableMap(map);
    }

    private String normalizeExerciseName(String aiSuggestion) {
        if (aiSuggestion == null || aiSuggestion.isBlank()) return aiSuggestion;

        // Limpeza: remove espaços extras e pontuação final comum (.,!)
        String cleanSuggestion = aiSuggestion.trim().replaceAll("[.,!?]$", "");
        String lowerSuggestion = cleanSuggestion.toLowerCase();

        // Tenta encontrar no mapa de sinónimos
        if (EXERCISE_MAP.containsKey(lowerSuggestion)) {
            return EXERCISE_MAP.get(lowerSuggestion);
        }

        // Lógica de fallback para casos dinâmicos (ex: nomes que contêm palavras-chave)
        if (lowerSuggestion.contains("puxada") && lowerSuggestion.contains("frente")) {
            return "Puxada à Frente";
        }

        return cleanSuggestion;
    }
}