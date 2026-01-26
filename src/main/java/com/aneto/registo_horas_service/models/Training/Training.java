package com.aneto.registo_horas_service.models.Training;

import com.aneto.registo_horas_service.dto.request.UserProfileRequest;
import com.aneto.registo_horas_service.dto.response.*;
import com.aneto.registo_horas_service.models.Enum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public record Training(ChatModel chatModel, ObjectMapper objectMapper) {

    public TrainingPlanResponse generateTrainingPlan(UserProfileRequest userRequest) {

        // 1. Sanitização de entradas
        String protocolId = defaultIfEmpty(userRequest.protocol(), "nasm_estabilizacao");
        String pathologyText = defaultIfEmpty(userRequest.pathology(), "Nenhuma limitação relatada");
        String historyText = defaultIfEmpty(userRequest.exerciseHistory(), "Não informado");
        String objectiveText = defaultIfEmpty(userRequest.objective(), "Manutenção de saúde e bem-estar");
        String locationText = defaultIfEmpty(userRequest.location(), "Não especificada");
        String countryText = defaultIfEmpty(userRequest.country(), "Não especificado");
        String bodyTypeText = defaultIfEmpty(userRequest.bodyType(), "Ectomorfo");
        String genderText = defaultIfEmpty(userRequest.gender(), "Não especificado");
        String weightKg = defaultIfEmpty(String.valueOf(userRequest.weightKg()), "Não especificado");


        // 2. Dados do Protocolo e Macros
        Enum.TrainingProtocol protocol = Enum.TrainingProtocol.fromId(protocolId);

        Double bodyFat = userRequest.bodyFat() != null ? userRequest.bodyFat() : 15.0;

        int mealsPerDay = userRequest.mealsPerDay() != null ? userRequest.mealsPerDay() : 6;

        Macros macros = MacroCalculator.calculate(
                userRequest.weightKg(), userRequest.heightCm(), userRequest.age(),
                genderText, bodyTypeText, bodyFat, mealsPerDay
        );

        // --- SEPARAÇÃO DAS REGRAS (DIRETRIZES) ---

        String descansoCientifico = calcularDescansoCientifico(protocol, objectiveText, weightKg);

        String diretrizProtocolo = """
                DIRETRIZES TÉCNICAS E FISIOLÓGICAS (%s):
                - Séries: %s | Repetições: %s
                - DESCANSO CIENTÍFICO: %s
                - RITMO (Tempo): %s
                - JUSTIFICATIVA FISIOLÓGICA: O descanso de %s é calculado para garantir a resíntese parcial de substratos energéticos (Fosfocreatina) enquanto mantém o estresse metabólico necessário para a sinalização hipertrófica.
                """.formatted(
                protocol.getLabel(),
                protocol.getSets(),
                protocol.getReps(),
                descansoCientifico,
                protocol.getTempo(),
                descansoCientifico
        );

        String diretrizBiomecanica = """
                DIRETRIZES DE SELEÇÃO BIOMECÂNICA E FILTRAGEM:
                1. REPERTÓRIO DO PROTOCOLO: %s. 
                   - IMPORTANTE: Usa estes exercícios APENAS se eles pertencerem ao grupo muscular do dia definido no campo "day".
                2. EXERCÍCIOS PROIBIDOS: %s.
                3. ADAPTAÇÃO: Sendo a patologia "%s", substitui impactos por baixo torque.
                4. COERÊNCIA ANATÓMICA: É estritamente proibido incluir exercícios de pernas (ex: Cadeira Extensora) em dias de Membros Superiores, e vice-versa.
                """.formatted(protocol.getSuggestedExercises(), protocol.getForbiddenExercises(), pathologyText);

        String diretrizAquecimento = """
                REGRA OBRIGATÓRIA DE AQUECIMENTO:
                - Se houver exercício de Reabilitação, este aquecimento deve ser o "order": 2.
                - Caso contrário, deve ser o "order": 1.
                - Se for Cárdio (ex: Passadeira): use reps como tempo (ex: "10 min").
                - Foco específico na região: %s.
                """.formatted(pathologyText);

// 1. Defina as duas diretrizes
        String diretrizTreino = getString(userRequest, pathologyText, protocol);

        String diretrizAlimentar = getString(macros);

        String diretrizRepertorio = """
                EXPANSÃO DE REPERTÓRIO:
                - Podes escolher qualquer exercício da tua base de dados que respeite a biomecânica do protocolo %s.
                - Objetivo: %s. Frequência: %d dias.
                """.formatted(protocol.getLabel(), objectiveText, userRequest.frequencyPerWeek());

        String diretrizEquipamento = """
                LOGÍSTICA DE EQUIPAMENTO:
                - Identifica o equipamento necessário no campo "equipment".
                - Localização: %s. Se for "Casa", foca em Peso Corporal/Halteres.
                """.formatted(locationText);

        String diretrizArrefecimento = """
                REGRA DE ARREFECIMENTO (VOLTA À CALMA):
                - O ÚLTIMO exercício de cada dia deve ser obrigatoriamente Arrefecimento ou Alongamento Estático.
                - No campo "reps", especifica o tempo (ex: "30-60 seg por posição").
                - No campo "details", descreve o ritmo: "Ritmo decrescente, foco na respiração profunda e relaxamento muscular".
                - Personaliza os alongamentos para os músculos solicitados no dia e para a patologia: %s.
                """.formatted(pathologyText);

        String diretrizIntensidade = """
                DIRETRIZ DE INTENSIDADE E ESFORÇO (RPE):
                - Deves atribuir obrigatoriamente um valor ao campo "intensity" para cada exercício.
                - Critérios de atribuição:
                  1. "BAIXA": Para o primeiro exercício (Mobilidade/Cárdio) e o último (Alongamento).
                  2. "MODERADA": Para exercícios acessórios ou técnicos.
                  3. "ALTA": Para exercícios multiarticulares base do protocolo %s.
                - Este campo controla a cor dinâmica na interface do aluno.
                """.formatted(protocol.getLabel());

        String diretrizNomenclaturaDias = """
                REGRAS CRÍTICAS DE DIVISÃO E NOMENCLATURA (Frequência %d):
                1. PADRÃO OBRIGATÓRIO NO CAMPO "day": "Dia X - [CATEGORIA]: [Foco Principal]"
                2. COERÊNCIA ANATÓMICA: É estritamente PROIBIDO misturar exercícios de pernas em dias de tronco. 
                3. DISTRIBUIÇÃO SUGERIDA:
                   - Se Frequência 6 (PPL 2x): Dia 1: PEITO/TRÍCEPS, Dia 2: COSTAS/BÍCEPS, Dia 3: PERNAS, Dia 4: OMBROS, Dia 5: SUPERIOR (Foco antagonistas), Dia 6: INFERIOR (Foco Posterior).
                   - Se Frequência 3: Dia 1: SUPERIOR (Empurrar), Dia 2: INFERIOR (Pernas), Dia 3: SUPERIOR (Puxar).
                4. ADAPTAÇÃO PATOLÓGICA: Sendo a patologia "%s", o Foco Principal de pelo menos um dia deve ser a reabilitação/mobilidade dessa zona.
                """.formatted(userRequest.frequencyPerWeek(), pathologyText);

        String diretrizReabilitacao = pathologyText.contains("Nenhuma") ?
                "Sendo um aluno sem limitações, foca o primeiro exercício (Order 1) em mobilidade geral ou ativação neuromuscular." :
                getString(pathologyText);

        // --- MONTAGEM FINAL DO PROMPT ---
        String userPrompt = """
                ESTRITA REGRA: RESPONDA APENAS O JSON.
                NÃO DIGA "OLÁ", NÃO DÊ EXPLICAÇÕES FORA DO JSON.
                
                Atue como um Personal Trainer e Nutricionista especialista em reabilitação física e metodologias de treino.
                Gere um plano de treino e um plano alimentar personalizado em Português.
                
                PERFIL DO ALUNO:
                - Idade: %d anos | Biótipo: %s | Género: %s
                - Altura: %.2f cm | Peso: %.2f kg
                - Histórico: %s | Patologias: %s
                - Localização: %s, %s | Objetivo: %s
                
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                
                FORMATO DE RESPOSTA (JSON APENAS):
                {
                 "summary": "Explicação técnica da estratégia %s (Fórmula: %s) para %s, considerando %d anos e a patologia %s.",
                  "plan": [
                    {
                      "day": "Dia 1 - SUPERIOR: Peitoral e Tríceps",
                      "exercises": [
                        {
                          "order": 1,
                          "name": "Nome",
                          "muscleGroup": "Grupo Muscular (Ex: Quadríceps, Isquiotibiais )",
                          "videoUrl": "https://www.youtube.com/results?search_query=dumbbell+incline+press+3d+anatomy",
                          "intensity": "ALTA | MODERADA | BAIXA",
                          "tempo": "%s",
                          "equipment": "Tipo de Equipamento",
                          "sets": "%s",
                          "reps": "%s",
                          "rest": "%s",
                          "details": "Dica de segurança para %s",
                          "notas": "JUSTIFICATIVA: Por que este exercício é ideal para %s e seguro para %s considerando o ritmo %s?"
                        }
                      ]
                    }
                  ],
                  "dietPlan": {
                    "methodology": "%s",
                    "dailyCalories": %d,
                    "imc": %.2f,
                    "imcCategory": "%s",
                    "statusSummary": "%s",
                    "macroDistribution": { "protein": "%dg", "carbs": "%dg", "fats": "%dg" },
                    "meals": [
                      {
                        "time": "HH:MM",
                        "description": "Nome da Refeição",
                        "ingredients": [
                          "Ex: 150g de Peito de Frango Grelhado - Fonte de proteína"
                        ],
                        "calories": 0,
                        "protein": 0,
                        "carbs": 0,
                        "fats": 0
                      }
                    ],
                    "localTips": "Dicas para %s, %s."
                  }
                }
                
                REGRAS CRÍTICAS PARA A DIETA:
                1. O campo "ingredients" DEVE ser preenchido com uma lista detalhada de alimentos e quantidades.
                2. Para cada refeição, os campos "calories", "protein", "carbs" e "fats" DEVEM ser números inteiros.
                3. A soma total deve ser aproximadamente: %d kcal, %dg Prot, %dg Carbs e %dg Fats.
                """.formatted(
                // 1-10: Perfil
                userRequest.age(), bodyTypeText, genderText, userRequest.heightCm(), userRequest.weightKg(),
                historyText, pathologyText, locationText, countryText, objectiveText,

                // 11-21: Blocos de Regras
                diretrizProtocolo, diretrizBiomecanica, diretrizAquecimento, diretrizTreino, diretrizAlimentar, diretrizRepertorio,
                diretrizEquipamento, diretrizArrefecimento, diretrizIntensidade, diretrizNomenclaturaDias, diretrizReabilitacao,

                // 22-26: JSON Summary
                protocol.getLabel(), macros.formula(), objectiveText, userRequest.age(), pathologyText,

                // 27-30: JSON Exercício
                protocol.getTempo(), protocol.getSets(), protocol.getReps(), protocol.getRest(),

                // 31-34: JSON Justificativas
                pathologyText, protocol.getLabel(), pathologyText, protocol.getTempo(),

                // 35-42: JSON Diet (Metodologia, Calorias, IMC, Categoria, Status, Macros)
                macros.formula(), macros.dailyCalories(), macros.imc(), macros.imcCategory(), macros.statusSummary(),
                macros.protein(), macros.carbs(), macros.fats(),

                // 43-44: Dicas Locais
                locationText, countryText,

                // 45-48: REGRAS CRÍTICAS FINAIS
                macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats()
        );
        return executeGeneration(userPrompt, userRequest);
    }

    @NotNull
    private static String getString(Macros macros) {
        StringBuilder dietTable = new StringBuilder();
        for (MealSuggestion m : macros.mealSuggestions()) { // Alterado para macros.meals() conforme o novo record
            dietTable.append("- %s (%s): %d kcal [P: %dg, C: %dg, G: %dg]\n".formatted(
                    m.name(),
                    m.time(),
                    (int) (macros.dailyCalories() * m.pctCalories()),
                    (int) (macros.protein() * m.pctProtein()),
                    (int) (macros.carbs() * m.pctCarbs()),
                    (int) (macros.fats() * m.pctFats())
            ));
        }

        return """
                ANÁLISE BIOMÉTRICA: %s
                CATEGORIA DE IMC: %s
                
                DIRETRIZES ALIMENTARES ESTRITAS (PROIBIDO ALTERAR VALORES):
                1. O plano deve conter EXATAMENTE estas %d refeições e valores:
                %s
                2. REGRA DE CÁLCULO: Para cada refeição, escolhe ingredientes que somem exatamente os valores acima.
                3. VALIDAÇÃO: A soma final das refeições no JSON deve ser: %d kcal, %dg P, %dg C, %dg G.
                4. FOCO NUTRICIONAL: %s
                """.formatted(
                macros.statusSummary(),
                macros.imcCategory(),
                macros.mealSuggestions().size(),
                dietTable.toString(),
                macros.dailyCalories(), macros.protein(), macros.carbs(), macros.fats(),
                macros.statusSummary() // Repetimos para dar ênfase
        );
    }


    @NotNull
    private static String getString(UserProfileRequest userRequest, String pathologyText, Enum.TrainingProtocol protocol) {
        String durationText = (userRequest.duration() != null && !userRequest.duration().isBlank())
                ? userRequest.duration() : "aprox. 60 minutos";

        // Lógica de mapeamento de dias conforme a frequência
        String divisaoSugerida = switch (userRequest.frequencyPerWeek()) {
            case 1, 2 -> "Dia 1: SUPERIOR (Empurrar/Puxar), Dia 2: INFERIOR (Pernas/Core).";
            case 3 -> "Dia 1: SUPERIOR (Empurrar), Dia 2: INFERIOR (Pernas), Dia 3: SUPERIOR (Puxar).";
            case 4 -> "Dia 1: PEITO/OMBRO, Dia 2: COSTAS/BÍCEPS, Dia 3: PERNAS (Quadríceps), Dia 4: POSTERIOR/CORE.";
            case 5 -> "Dia 1: PEITO, Dia 2: COSTAS, Dia 3: PERNAS, Dia 4: OMBROS, Dia 5: BRAÇOS/CORE.";
            default ->
                    "Dia 1: PEITO/TRÍCEPS, Dia 2: COSTAS/BÍCEPS, Dia 3: PERNAS (Foco Quadríceps), Dia 4: OMBROS, Dia 5: PERNAS (Foco Posterior), Dia 6: FULL BODY/REABILITAÇÃO.";
        };

        return """
                DIRETRIZES DE ESTRUTURA E BIOMECÂNICA (REGRAS CRÍTICAS):
                1. DIVISÃO E DURAÇÃO: Exatamente %d dias por semana, sessões de %s.
                2. COERÊNCIA ANATÓMICA (ESTRITA): É PROIBIDO incluir exercícios de membros inferiores (ex: Cadeira Extensora, Agachamento) em dias marcados como "SUPERIOR" ou "PEITO/COSTAS".
                3. MAPA DE TREINO OBRIGATÓRIO:
                   - %s
                4. VARIAÇÃO DE PLANOS: Cada treino deve navegar entre o PLANO SAGITAL, FRONTAL e TRANSVERSAL para equilíbrio articular.
                5. REABILITAÇÃO E AQUECIMENTO (ORDER 1): 
                   - O primeiro exercício de cada dia DEVE ser específico para a patologia "%s" E para os músculos que serão treinados no dia.
                   - Exemplo: Se o dia é PEITO e a patologia é OMBRO, o 'order 1' deve ser Mobilidade de Ombro/Manguito.
                6. LÓGICA DO PROTOCOLO: Filtra os exercícios do protocolo %s para que apareçam apenas nos dias anatomicamente corretos.
                """.formatted(
                userRequest.frequencyPerWeek(),
                durationText,
                divisaoSugerida,
                pathologyText,
                protocol.getLabel()
        );
    }

    @NotNull
    private static String getString(String pathologyText) {
        if (pathologyText == null || pathologyText.equalsIgnoreCase("Nenhuma") || pathologyText.isBlank()) {
            return "";
        }

        StringBuilder correcao = new StringBuilder();
        String lowerPathology = pathologyText.toLowerCase();

        // 1. JOELHO (Valgo/Varo/Estabilidade)
        if (lowerPathology.contains("valgo") || lowerPathology.contains("joelho")) {
            correcao.append("""
                    - DIRETRIZ JOELHO: Foco em estabilizadores da anca (Glúteo Médio) e Vasto Medial.
                    - GRUPOS MUSCULARES ALVO: Abdutores e Quadríceps.
                    - OBRIGATÓRIO: Incluir exercícios no PLANO FRONTAL (ex: Clamshells ou Side-walk).
                    """);
        }

        // 2. TORNOZELO (Instabilidade/Entorse/Mobilidade)
        if (lowerPathology.contains("tornozelo") || lowerPathology.contains("entorse") || lowerPathology.contains("pé")) {
            correcao.append("""
                    - DIRETRIZ TORNOZELO: Foco em Propriocepção e Fortalecimento da cadeia inferior.
                    - GRUPOS MUSCULARES ALVO: Tibial Anterior, Peroniais e Gastrocnémios.
                    - OBRIGATÓRIO: Exercícios de equilíbrio e dorsiflexão.
                    """);
        }

        // 3. OMBRO (Manguito Rotador/Impacto)
        if (lowerPathology.contains("ombro") || lowerPathology.contains("manguito") || lowerPathology.contains("escápula")) {
            correcao.append("""
                    - DIRETRIZ OMBRO: Foco na estabilidade da cintura escapular.
                    - GRUPOS MUSCULARES ALVO: Manguito Rotador (Subescapular, Supraespinhoso, Infraespinhoso) e Serrátil Anterior.
                    - OBRIGATÓRIO: Rotações externas e estabilização isométrica.
                    """);
        }

        // 4. PESCOÇO / CERVICAL
        if (lowerPathology.contains("pescoço") || lowerPathology.contains("cervical")) {
            correcao.append("""
                    - DIRETRIZ CERVICAL: Corrigir anteriorização e hipercifose.
                    - GRUPOS MUSCULARES ALVO: Flexores profundos do pescoço e Trapézio Inferior/Médio.
                    - OBRIGATÓRIO: Chin Tucks e Retração Escapular (Y-W-T).
                    """);
        }

        // 5. COLUNA (Lombar/Hérnia)
        if (lowerPathology.contains("lombar") || lowerPathology.contains("hérnia") || lowerPathology.contains("costas")) {
            correcao.append("""
                    - DIRETRIZ COLUNA: Foco em estabilização segmentar e "Bracing".
                    - GRUPOS MUSCULARES ALVO: Transverso do abdómen, Multífidos e Eretores da espinha.
                    - OBRIGATÓRIO: Pranchas (Plank) e Dead Bug (evitar flexão excessiva se houver dor).
                    """);
        }

        return """
                REGRAS ANATÓMICAS CRÍTICAS PARA REABILITAÇÃO:
                %s
                1. DIFERENCIAÇÃO: Nunca listes uma articulação (ex: "Tornozelo") no campo 'muscleGroup'. Usa sempre o músculo motor (ex: "Tibial", "Gémeos").
                2. ORDEM 1: Como o aluno possui "%s", o primeiro exercício de cada dia deve ser a correção motora indicada acima.
                3. JUSTIFICATIVA: No campo 'notas', explica a biomecânica (ex: "Fortalecimento do Tibial para estabilizar a articulação do tornozelo").
                """.formatted(correcao.toString(), pathologyText);
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
    private TrainingPlanResponse executeGeneration(String prompt, UserProfileRequest userRequest) {
        try {
            String textResponse = chatModel.call(prompt);
            String cleanedJson = cleanMarkdown(textResponse);

            // 1. Desserialização inicial
            // O Jackson lê o JSON da IA e preenche o objeto temporário
            TrainingPlanResponse response = objectMapper.readValue(cleanedJson, TrainingPlanResponse.class);

            // 2. Reconstrução do Plano (Injetando links de Anatomia 3D)
            // Usamos Streams para navegar na estrutura imutável de Records
            List<TrainingDay> updatedPlan = response.getPlan().stream()
                    .map(day -> {
                        List<TrainingExercise> enrichedExercises = day.exercises().stream()
                                .map(ex -> {
                                    // Busca o link bonito de fibras musculares no Enum
                                    String anatomyLink = EnumExerciseCategory.findLinkByExerciseName(ex.name());

                                    // Cria uma nova instância do record com o link preenchido
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
                                            ex.weight(),
                                            ex.cargaAtual(),
                                            anatomyLink, // Link injetado aqui
                                            ex.date()
                                    );
                                })
                                .toList();

                        return new TrainingDay(day.day(), enrichedExercises);
                    })
                    .toList();

            // 3. Retorno com todos os 5 campos exigidos pelo @AllArgsConstructor
            return new TrainingPlanResponse(
                    false,                        // isExistingPlan (primeiro campo da classe)
                    response.getSummary(),        // summary
                    updatedPlan,                  // plan (a lista que acabámos de reconstruir)
                    response.getDietPlan(),       // dietPlan
                    userRequest                   // userProfile (conforme solicitado)
            );

        } catch (Exception e) {
            System.err.println("Erro na geração ou no mapeamento: " + e.getMessage());
            throw new RuntimeException("Falha ao processar plano de treino: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) return "{}";

        // Remove blocos de código
        String cleaned = text.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();

        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned;
    }

    private String calcularDescansoCientifico(Enum.TrainingProtocol protocol, String objective, String cargaAtual) {
        // 1. Converter carga para número (remover 'kg' se existir)
        double peso = 0;
        try {
            peso = Double.parseDouble(cargaAtual.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            peso = 0;
        }

        // 2. Lógica FST-7 (Protocolo rígido: foco em pump)
        if (protocol.getLabel().contains("FST-7")) {
            // Mesmo com carga alta, o FST-7 exige descanso curto para hipóxia
            return "30";
        }

        // 3. Lógica baseada em Carga vs Objetivo
        if (objective.equalsIgnoreCase("Hipertrofia")) {
            // Se o peso for muito elevado (ex: > 80kg num exercício composto),
            // aumentamos o descanso para garantir qualidade na próxima série.
            if (peso > 80) return "90";
            if (peso > 40) return "60";
            return "45"; // Cargas leves/isoladores
        }

        if (objective.equalsIgnoreCase("Força")) {
            // Na força, o descanso escala drasticamente com o peso
            if (peso > 100) return "300"; // 5 min para agachamentos/supinos pesados
            if (peso > 50) return "180";  // 3 min
            return "120";                // 2 min
        }

        // Fallback limpo (apenas números)
        return protocol.getRest().replaceAll("[^0-9]", "");
    }
}