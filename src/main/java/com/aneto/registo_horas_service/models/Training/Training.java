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

import java.util.List;

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

        // 2. Cálculos Nutricionais e Macros
        Macros macros = MacroCalculator.calculate(
                userRequest.weightKg(),
                userRequest.heightCm(),
                userRequest.age(),
                genderText,
                bodyTypeText,
                userRequest.bodyFat() != null ? userRequest.bodyFat() : 15.0,
                userRequest.mealsPerDay() != null ? userRequest.mealsPerDay() : 6
        );

        // 3. Lógica de Variação (Anti-Platô) e NASM
        String tempoNASM = protocolId.contains("estabilizacao") ? "4-2-1" : "2-0-2";
        String listaParaEvitar = (exerciciosDoS3 == null || exerciciosDoS3.isEmpty())
                ? "Nenhum (Primeiro plano do aluno)"
                : String.join(", ", exerciciosDoS3);

        String diretrizVariedade = """
                [SISTEMA DE VARIAÇÃO ANTI-PLATÔ]
                - EXERCÍCIOS JÁ REALIZADOS (NÃO REPETIR): %s.
                - TEMA DESTE NOVO PLANO: Focar em variações biomecânicas (ex: se usou barra, use halteres; se foi estável, use unipodal).
                - OBRIGATÓRIO: O campo 'tempo' no JSON deve ser rigorosamente '%s' para respeitar a fisiologia da fase.
                """.formatted(listaParaEvitar, tempoNASM);

        // 4. Adaptação para Sedentários (Prevenção de mal-estar)
        boolean isSedentary = "sedentary".equalsIgnoreCase(userRequest.exerciseHistory());
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
                REGRA OBRIGATÓRIA DE AQUECIMENTO:
                - Se houver Reabilitação, "order": 2. Caso contrário, "order": 1.
                - Foco específico na região: %s.
                """.formatted(pathologyText);

        String diretrizTreino = getString(userRequest, pathologyText, protocol, durationText, descansoEfetivo, volumeIdeal);
        String diretrizAlimentar = getString(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Escolha exercícios que respeitem o protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.frequencyPerWeek());

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

        String diretrizNomenclaturaDias = """
                REGRAS ESTRITAS DE DIVISÃO (FREQUÊNCIA %d):
                1. NOMENCLATURA: "Dia 1 - PUSH: Peito/Ombros/Tríceps", "Dia 2 - PULL: Costas/Bíceps", "Dia 3 - LEGS: Pernas e Core".
                2. REABILITAÇÃO DIÁRIA: Todos os dias DEVEM começar (Order 1) com um exercício para %s.
                3. VARIABILIDADE: Cada bloco de treino deve ter exercícios 100%% únicos.
                4. QUANTIDADE: Gera exatamente %d blocos no array "plan".
                """.formatted(userRequest.frequencyPerWeek(), pathologyText, userRequest.frequencyPerWeek());

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
        String blocoDiretrizesCompletas = String.join("\n",
                diretrizVariedade, diretrizSegurancaIniciante, diretrizProtocolo, diretrizReabilitacao,
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
                userRequest.age(), bodyTypeText, genderText, userRequest.weightKg(), objectiveText, pathologyText,
                blocoDiretrizesCompletas, regrasFinais,
                protocol.getLabel(), protocol.getTempo(), descansoEfetivo,
                macros.dailyCalories(), macros.imc(), macros.imcCategory(),
                macros.protein(), macros.carbs(), macros.fats()
        );

        return executeGeneration(userPrompt, userRequest, totalMinutos);
    }

    private TrainingPlanResponse executeGeneration(String prompt, UserProfileRequest userRequest, int totalMinutos) {
        log.info("Iniciando executeGeneration no ChatModel.");
        int maxRetries = 5;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String textResponse = chatModel.call(prompt);
                String cleanedJson = cleanMarkdown(textResponse);
                TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

                // 1. VALIDAÇÃO DE VOLUME
                int minExAceitavel = Math.max(4, (totalMinutos / 10));
                boolean isVolumeValido = response.getPlan() != null && !response.getPlan().isEmpty() &&
                        response.getPlan().stream().allMatch(d -> d.exercises() != null && d.exercises().size() >= minExAceitavel);

                if (!isVolumeValido) throw new RuntimeException("Volume insuficiente.");

                // 2. MAPEAMENTO E "STRICT CHECK" DE INVENTÁRIO
                int finalAttempt = attempt;
                List<TrainingDay> updatedPlan = response.getPlan().stream()
                        .map(day -> {
                            List<TrainingExercise> enrichedExercises = day.exercises().stream()
                                    .map(ex -> {
                                        // 1. NORMALIZAÇÃO PREVENTIVA (Evita que "Prancha" falhe se existir "Prancha Abdominal")
                                        String correctedName = normalizeExerciseName(ex.name());
                                        // Busca a URL (olha no Redis -> depois DB -> por fim gera fallback)
                                        String finalUrl = exerciseVideoService.getVideoUrl(correctedName);

                                        // --- STRICT CHECK ---
                                        // Se a URL contiver 'results?search_query', a IA inventou um nome fora da lista
                                        if (finalUrl.contains("youtube.com/results")) {
                                            log.error("[ALERTA DE INVENTÁRIO] A IA usou '{}', que não existe na base. Tentativa: {}", ex.name(), finalAttempt);
                                            throw new RuntimeException("Exercício inválido detectado: " + ex.name());
                                        }
                                        // --------------------

                                        return new TrainingExercise(
                                                ex.order(), correctedName, ex.muscleGroup(), ex.equipment(),
                                                ex.intensity(), ex.sets(), ex.reps(), ex.rest(),
                                                ex.tempo(), ex.details(), ex.notas(), ex.weight(),
                                                ex.cargaAtual(), finalUrl,
                                                java.time.LocalDate.now().toString(),
                                                ex.movementPlane()
                                        );
                                    }).toList();
                            return new TrainingDay(day.day(), enrichedExercises);
                        }).toList();

                log.info("Geração concluída com sucesso e validada contra inventário.");
                return new TrainingPlanResponse(false, response.getSummary(), updatedPlan, response.getDietPlan(), userRequest);

            } catch (Exception e) {
                log.warn("Falha na tentativa {}/{} - Erro: {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("Todas as tentativas falharam. Erro final: {}", e.getMessage());
                    throw new RuntimeException("Falha crítica: A IA não conseguiu gerar um plano usando apenas os exercícios permitidos.");
                }

                // Reforça a instrução de restrição no prompt para a próxima tentativa
                prompt += "\n\nERRO NA TENTATIVA ANTERIOR: " + e.getMessage() +
                        "\nPOR FAVOR, USA APENAS OS NOMES EXATOS DO DICIONÁRIO FORNECIDO.";
            }
        }
        throw new RuntimeException("Falha na geração.");
    }

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
        String divisao = switch (userRequest.frequencyPerWeek()) {
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

    private String normalizeExerciseName(String aiSuggestion) {
        if (aiSuggestion == null || aiSuggestion.isBlank()) return aiSuggestion;

        String normalized = aiSuggestion.trim();
        String lower = normalized.toLowerCase();

        // Mapeamentos comuns baseados nos teus logs de erro
        if (lower.equals("prancha")) return "Prancha Abdominal";
        if (lower.contains("alongamento")) return "Y-W-T"; // Ou o exercício de mobilidade que preferires
        if (lower.contains("cat-cow")) return "Cat Cow";
        if (lower.contains("bird-dog")) return "Bird Dog";
        if (lower.contains("puxada") && lower.contains("frente")) return "Puxada à Frente";

        return normalized;
    }
}